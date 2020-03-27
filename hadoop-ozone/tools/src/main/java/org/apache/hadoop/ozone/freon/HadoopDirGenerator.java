/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership.  The ASF
 * licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.hadoop.ozone.freon;

import java.net.URI;
import java.util.concurrent.Callable;

import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.hdds.cli.HddsVersionProvider;
import org.apache.hadoop.hdds.conf.OzoneConfiguration;

import org.apache.commons.lang3.RandomStringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * Directory generator tool test om performance.
 */
@Command(name = "ddsg",
    aliases = "dfs-directory-generator",
    description =
            "Create random directories to the any dfs compatible file system.",
    versionProvider = HddsVersionProvider.class,
    mixinStandardHelpOptions = true,
    showDefaultValues = true)
public class HadoopDirGenerator extends BaseFreonGenerator
    implements Callable<Void> {

  private static final Logger LOG =
      LoggerFactory.getLogger(HadoopDirGenerator.class);

  @Option(names = {"--dpath"},
      description = "Hadoop FS directory system path",
      defaultValue = "o3fs://bucket2.vol2")
  private String rootPath;


  @Option(names = {"-d", "--depth"},
      description = "Number of directories to be generated recursively",
      defaultValue = "5")
  private int depth;

  @Option(names = {"--span"},
      description =
              "Number of child directories to be created in leaf directory.",
      defaultValue = "10")
  private int span;

  @Option(names = {"--nameLen"},
      description =
              "Length of the random name of directory you want to create.",
      defaultValue = "10")
  private int n;

  private FileSystem fileSystem;

  @Override
  public Void call() throws Exception {

    init();
    OzoneConfiguration configuration = createOzoneConfiguration();
    fileSystem = FileSystem.get(URI.create(rootPath), configuration);
    runTests(this::createDir);
    return null;

  }

  private void createDir(long counter) throws Exception {
    String dirString = RandomStringUtils.randomAlphanumeric(n);
    for(int i = 1; i <= depth; i++) {
      dirString = dirString.concat("/").concat(RandomStringUtils.
              randomAlphanumeric(n));
    }
    Path file = new Path(rootPath.concat("/").concat(dirString));
    fileSystem.mkdirs(file.getParent());
    String leafDir = dirString.substring(0, dirString.length() - n);
    String tmp = "/0";
    for(int i = 1; i <= span; i++) {
      String childDir = leafDir.concat(Integer.toString(i)).concat(tmp);
      Path dir = new Path(rootPath.concat("/").concat(childDir));
      fileSystem.mkdirs(dir.getParent());
    }
  }
}
