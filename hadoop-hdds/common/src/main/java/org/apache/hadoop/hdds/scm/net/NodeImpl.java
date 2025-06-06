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

package org.apache.hadoop.hdds.scm.net;

import static org.apache.hadoop.hdds.scm.net.NetConstants.BYTE_STRING_ROOT;
import static org.apache.hadoop.hdds.scm.net.NetConstants.PATH_SEPARATOR_STR;

import com.google.common.base.Preconditions;
import org.apache.hadoop.hdds.protocol.proto.HddsProtos;
import org.apache.hadoop.ozone.util.StringWithByteString;

/**
 * A thread safe class that implements interface Node.
 */
public class NodeImpl implements Node {
  // host:port#
  private StringWithByteString name;
  // string representation of this node's location, such as /dc1/rack1
  private StringWithByteString location;
  // location + "/" + name
  private String path;
  // which level of the tree the node resides, start from 1 for root
  private int level;
  // node's parent
  private InnerNode parent;
  // the cost to go through this node
  private final int cost;

  /**
   * Construct a node from its name and its location.
   * @param name this node's name (can be null, must not contain
   * {@link NetConstants#PATH_SEPARATOR})
   * @param location this node's location
   */
  public NodeImpl(StringWithByteString name, StringWithByteString location, int cost) {
    if (name != null && name.getString().contains(PATH_SEPARATOR_STR)) {
      throw new IllegalArgumentException(
          "Network location name:" + name + " should not contain " +
              PATH_SEPARATOR_STR);
    }
    this.name = name == null ? BYTE_STRING_ROOT : name;
    this.location = location;
    this.path = getPath();
    this.cost = cost;
  }

  public NodeImpl(String name, String location, int cost) {
    this(StringWithByteString.valueOf(name), StringWithByteString.valueOf(NetUtils.normalize(location)), cost);
  }

  /**
   * Construct a node from its name and its location.
   *
   * @param name     this node's name (can be null, must not contain
   *                 {@link NetConstants#PATH_SEPARATOR})
   * @param location this node's location
   * @param parent   this node's parent node
   * @param level    this node's level in the tree
   * @param cost     this node's cost if traffic goes through it
   */
  public NodeImpl(String name, String location, InnerNode parent, int level,
      int cost) {
    this(name, location, cost);
    this.parent = parent;
    this.level = level;
  }

  public NodeImpl(StringWithByteString name, StringWithByteString location, InnerNode parent, int level,
      int cost) {
    this(name, location, cost);
    this.parent = parent;
    this.level = level;
  }

  /**
   * @return this node's name
   */
  @Override
  public String getNetworkName() {
    return name.getString();
  }

  /**
   * @return this node's name
   */
  public StringWithByteString getNetworkNameAsByteString() {
    return name;
  }

  /**
   * Set this node's name, can be hostname or Ipaddress.
   * @param networkName it's network name
   */
  @Override
  public void setNetworkName(String networkName) {
    this.name = StringWithByteString.valueOf(networkName);
    this.path = getPath();
  }

  /**
   * Set this node's name, can be hostname or Ipaddress.
   * @param networkName it's network name
   */
  public void setNetworkName(StringWithByteString networkName) {
    this.name = networkName;
    this.path = getPath();
  }

  /**
   * @return this node's network location
   */
  @Override
  public String getNetworkLocation() {
    return location.getString();
  }

  /**
   * @return this node's network location
   */
  public StringWithByteString getNetworkLocationAsByteString() {
    return location;
  }

  /**
   * Set this node's network location.
   * @param networkLocation it's network location
   */
  @Override
  public void setNetworkLocation(String networkLocation) {
    this.location = StringWithByteString.valueOf(networkLocation);
    this.path = getPath();
  }

  /**
   * @return this node's full path in network topology. It's the concatenation
   * of location and name.
   */
  @Override
  public String getNetworkFullPath() {
    return path;
  }

  /**
   * @return this node's parent
   */
  @Override
  public InnerNode getParent() {
    return parent;
  }

  /**
   * @return this node's ancestor, generation 0 is itself, generation 1 is
   * node's parent, and so on.
   */
  @Override
  public Node getAncestor(int generation) {
    Preconditions.checkArgument(generation >= 0);
    Node current = this;
    while (generation > 0 && current != null) {
      current = current.getParent();
      generation--;
    }
    return current;
  }

  /**
   * Set this node's parent.
   *
   * @param parent the parent
   */
  @Override
  public void setParent(InnerNode parent) {
    this.parent = parent;
  }

  /**
   * @return this node's level in the tree.
   * E.g. the root of a tree returns 0 and its children return 1
   */
  @Override
  public int getLevel() {
    return this.level;
  }

  /**
   * Set this node's level in the tree.
   *
   * @param level the level
   */
  @Override
  public void setLevel(int level) {
    this.level = level;
  }

  /**
   * @return this node's cost when network traffic go through it.
   * E.g. the cost of going cross a switch is 1, and cost of going through a
   * datacenter is 5.
   * Be default the cost of leaf datanode is 0, all other inner node is 1.
   */
  @Override
  public int getCost() {
    return this.cost;
  }

  /** @return the leaf nodes number under this node. */
  @Override
  public int getNumOfLeaves() {
    return 1;
  }

  /**
   * Check if this node is an ancestor of node <i>node</i>. Ancestor includes
   * itself and parents case;
   * @param node a node
   * @return true if this node is an ancestor of <i>node</i>
   */
  @Override
  public boolean isAncestor(Node node) {
    if (node == null) {
      return false;
    }
    return isAncestor(node.getNetworkFullPath());
  }

  @Override
  public boolean isAncestor(String nodePath) {
    if (nodePath == null) {
      return false;
    }
    return this.getNetworkFullPath().equals(PATH_SEPARATOR_STR) ||
        nodePath.equalsIgnoreCase(this.getNetworkFullPath()) ||
        NetUtils.addSuffix(nodePath).startsWith(
            NetUtils.addSuffix(this.getNetworkFullPath()));
  }

  @Override
  public boolean isDescendant(Node node) {
    if (node == null) {
      return false;
    }
    return isDescendant(node.getNetworkFullPath());
  }

  @Override
  public boolean isDescendant(String nodePath) {
    if (nodePath == null) {
      return false;
    }
    return NetUtils.addSuffix(this.getNetworkFullPath()).startsWith(
        NetUtils.addSuffix(nodePath));
  }

  public static HddsProtos.NodeTopology toProtobuf(String name, String location,
      int level, int cost) {

    HddsProtos.NodeTopology.Builder nodeTopologyBuilder =
        HddsProtos.NodeTopology.newBuilder()
            .setName(name)
            .setLocation(location)
            .setLevel(level)
            .setCost(cost);

    HddsProtos.NodeTopology nodeTopology = nodeTopologyBuilder.build();
    return nodeTopology;
  }

  @Override
  public boolean equals(Object to) {
    if (to == null) {
      return false;
    }
    if (this == to) {
      return true;
    }
    return this.toString().equals(to.toString());
  }

  @Override
  public int hashCode() {
    return toString().hashCode();
  }

  /**
   * @return this node's path as its string representation
   */
  @Override
  public String toString() {
    return getNetworkFullPath();
  }

  private String getPath() {
    return this.location.getString().equals(PATH_SEPARATOR_STR) ?
        this.location + this.name.getString() :
        this.location + PATH_SEPARATOR_STR + this.name;
  }
}
