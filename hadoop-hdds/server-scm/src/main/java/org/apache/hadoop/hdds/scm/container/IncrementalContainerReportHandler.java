/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.hadoop.hdds.scm.container;

import java.io.IOException;
import org.apache.hadoop.hdds.protocol.DatanodeDetails;
import org.apache.hadoop.hdds.protocol.proto.StorageContainerDatanodeProtocolProtos.ContainerReplicaProto;
import org.apache.hadoop.hdds.scm.container.report.ContainerReportValidator;
import org.apache.hadoop.hdds.scm.exceptions.SCMException;
import org.apache.hadoop.hdds.scm.ha.SCMContext;
import org.apache.hadoop.hdds.scm.node.NodeManager;
import org.apache.hadoop.hdds.scm.node.states.NodeNotFoundException;
import org.apache.hadoop.hdds.scm.server.SCMDatanodeHeartbeatDispatcher.IncrementalContainerReportFromDatanode;
import org.apache.hadoop.hdds.server.events.EventHandler;
import org.apache.hadoop.hdds.server.events.EventPublisher;
import org.apache.hadoop.ozone.common.statemachine.InvalidStateTransitionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles incremental container reports from datanode.
 */
public class IncrementalContainerReportHandler extends
    AbstractContainerReportHandler
    implements EventHandler<IncrementalContainerReportFromDatanode> {

  private static final Logger LOG = LoggerFactory.getLogger(
      IncrementalContainerReportHandler.class);

  private final NodeManager nodeManager;

  public IncrementalContainerReportHandler(
      final NodeManager nodeManager,
      final ContainerManager containerManager,
      final SCMContext scmContext) {
    super(containerManager, scmContext, LOG);
    this.nodeManager = nodeManager;
  }

  @Override
  public void onMessage(final IncrementalContainerReportFromDatanode report,
                        final EventPublisher publisher) {
    final DatanodeDetails dnFromReport = report.getDatanodeDetails();
    if (LOG.isDebugEnabled()) {
      LOG.debug("Processing incremental container report from data node {}", dnFromReport);
    }
    final DatanodeDetails dd = nodeManager.getNode(dnFromReport.getID());
    if (dd == null) {
      LOG.warn("Received container report from unknown datanode {}",
          dnFromReport);
      return;
    }

    boolean success = false;
    // HDDS-5249 - we must ensure that an ICR and FCR for the same datanode
    // do not run at the same time or it can result in a data consistency
    // issue between the container list in NodeManager and the replicas in
    // ContainerManager.
    synchronized (dd) {
      for (ContainerReplicaProto replicaProto :
          report.getReport().getReportList()) {
        ContainerID id = ContainerID.valueOf(replicaProto.getContainerID());
        ContainerInfo container = null;
        try {
          try {
            container = getContainerManager().getContainer(id);
            // Ensure we reuse the same ContainerID instance in containerInfo
            id = container.containerID();
          } finally {
            if (replicaProto.getState().equals(
                ContainerReplicaProto.State.DELETED)) {
              nodeManager.removeContainer(dd, id);
            } else {
              nodeManager.addContainer(dd, id);
            }
          }
          if (ContainerReportValidator.validate(container, dd, replicaProto)) {
            processContainerReplica(dd, container, replicaProto, publisher);
          }
          success = true;
        } catch (ContainerNotFoundException e) {
          LOG.warn("Container {} not found!", replicaProto.getContainerID());
        } catch (NodeNotFoundException ex) {
          LOG.error("Received ICR from unknown datanode {}",
              report.getDatanodeDetails(), ex);
        } catch (ContainerReplicaNotFoundException e) {
          LOG.warn("Container {} replica not found!",
              replicaProto.getContainerID());
        } catch (SCMException ex) {
          if (ex.getResult() == SCMException.ResultCodes.SCM_NOT_LEADER) {
            LOG.info("Failed to process {} container {}: {}",
                replicaProto.getState(), id, ex.getMessage());
          } else {
            LOG.error("Exception while processing ICR for container {}",
                replicaProto.getContainerID(), ex);
          }
        } catch (IOException | InvalidStateTransitionException e) {
          LOG.error("Exception while processing ICR for container {}",
              replicaProto.getContainerID(), e);
        }
      }
    }

    getContainerManager().notifyContainerReportProcessing(false, success);
  }

  protected NodeManager getNodeManager() {
    return this.nodeManager;
  }
}
