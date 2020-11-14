/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2020  Dirk Beyer
 *  All rights reserved.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.sosy_lab.cpachecker.cfa.mutation.strategy;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.SortedSetMultimap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.cfa.ParseResult;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.cfa.model.FunctionEntryNode;
import org.sosy_lab.cpachecker.cfa.postprocessing.function.ThreadCreateTransformer;
import org.sosy_lab.cpachecker.cfa.postprocessing.function.ThreadCreateTransformer.ThreadFinder;
import org.sosy_lab.cpachecker.util.CFATraversal;
import org.sosy_lab.cpachecker.util.Pair;

@Options
public class FunctionStrategy
    extends GenericCFAMutationStrategy<String, Pair<FunctionEntryNode, SortedSet<CFANode>>> {
  @Option(
      secure = true,
      name = "cfa.mutations.functionsWhitelist",
      description = "Names of functions (separated with comma) that should not be deleted from CFA")
  private Set<String> whitelist = ImmutableSet.of("main");

  @Option(
      secure = true,
      name = "analysis.threadOperationsTransform",
      description =
          "Replace thread creation operations with a special function calls"
              + "so, any analysis can go through the function")
  private boolean enableThreadOperationsInstrumentation = false;

  @Option(
      secure = true,
      name = "cfa.threads.threadCreate",
      description = "A name of thread_create function")
  private String threadCreate = "pthread_create";

  @Option(
      secure = true,
      name = "cfa.threads.threadSelfCreate",
      description = "A name of thread_create_N function")
  private String threadCreateN = "pthread_create_N";

  /*
   * @Option( secure = true, name = "cfa.threads.threadJoin", description =
   * "A name of thread_join function") private String threadJoin = "pthread_join";
   *
   * @Option( secure = true, name = "cfa.threads.threadSelfJoin", description =
   * "A name of thread_join_N function") private String threadJoinN = "pthread_join_N";
   */

  public FunctionStrategy(Configuration pConfig, LogManager pLogger)
      throws InvalidConfigurationException {
    super(pConfig, pLogger, "Functions");
    pConfig.inject(this);
  }

  public FunctionStrategy(
      Configuration pConfig,
      LogManager pLogger,
      final Set<String> pWhitelist)
      throws InvalidConfigurationException {
    super(pConfig, pLogger, "Functions");
    pConfig.inject(this);
    whitelist = pWhitelist;
  }

  @Override
  protected Collection<String> getAllObjects(ParseResult pParseResult) {

    List<String> result = new ArrayList<>(pParseResult.getFunctions().keySet());
    result.removeAll(whitelist);

    if (enableThreadOperationsInstrumentation) {
      String dummy = "dummy_thread_join_function";
      final ThreadFinder threadVisitor =
          new ThreadFinder(threadCreate, threadCreateN, dummy, dummy);

      for (FunctionEntryNode functionStartNode : pParseResult.getFunctions().values()) {
        CFATraversal.dfs().traverseOnce(functionStartNode, threadVisitor);
      }

      Set<String> threadedFunctions =
          threadVisitor.getThreadNames()
          .stream()
          .map(ThreadCreateTransformer::getThreadName)
              .collect(Collectors.toSet());
      result.removeAll(threadedFunctions);
    }

    SortedSetMultimap<String, CFANode> allNodes = pParseResult.getCFANodes();
    Collections.sort(result, (a, b) -> allNodes.get(a).size() - allNodes.get(b).size());
    return result;
  }

  @Override
  protected Pair<FunctionEntryNode, SortedSet<CFANode>> removeObject(
      ParseResult pParseResult, String functionName) {
    FunctionEntryNode entry = pParseResult.getFunctions().get(functionName);
    SortedSet<CFANode> nodes = new TreeSet<>(pParseResult.getCFANodes().get(functionName));
    logger.logf(
        logObjects, "removing %s (entry is %s, %d nodes)", functionName, entry, nodes.size());
    pParseResult.getCFANodes().removeAll(functionName);
    pParseResult.getFunctions().remove(functionName);

    return Pair.of(entry, nodes);
  }

  @Override
  protected void returnObject(
      ParseResult pParseResult, Pair<FunctionEntryNode, SortedSet<CFANode>> pRollbackInfo) {
    String functionName = pRollbackInfo.getFirst().getFunctionName();
    logger.logf(
        logObjects,
        "returning %s (entry is %s, %d nodes)",
        functionName,
        pRollbackInfo.getFirst(),
        pRollbackInfo.getSecond().size());
    pParseResult.getCFANodes().putAll(functionName, pRollbackInfo.getSecond());
    pParseResult.getFunctions().put(functionName, pRollbackInfo.getFirst());
  }
}
