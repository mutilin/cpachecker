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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.logging.Level;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.cfa.ParseResult;
import org.sosy_lab.cpachecker.cfa.ast.c.CAssignment;
import org.sosy_lab.cpachecker.cfa.ast.c.CCastExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionCallExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionCallStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CIdExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CRightHandSide;
import org.sosy_lab.cpachecker.cfa.ast.c.CStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CUnaryExpression;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.cfa.model.FunctionEntryNode;
import org.sosy_lab.cpachecker.cfa.model.c.CStatementEdge;
import org.sosy_lab.cpachecker.util.CFATraversal;
import org.sosy_lab.cpachecker.util.CFATraversal.TraversalProcess;
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
  private Set<String> threadCreate = ImmutableSet.of("pthread_create");

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

  private class ThreadFinder implements CFATraversal.CFAVisitor {
    Collection<String> threadedFunctions = new HashSet<>();

    @Override
    public TraversalProcess visitEdge(CFAEdge pEdge) {
      if (pEdge instanceof CStatementEdge) {
        CStatement statement = ((CStatementEdge) pEdge).getStatement();
        if (statement instanceof CAssignment) {
          CRightHandSide rhs = ((CAssignment) statement).getRightHandSide();
          if (rhs instanceof CFunctionCallExpression) {
            CFunctionCallExpression exp = ((CFunctionCallExpression) rhs);
            checkFunctionExpression(exp);
          }
        } else if (statement instanceof CFunctionCallStatement) {
          CFunctionCallExpression exp =
              ((CFunctionCallStatement) statement).getFunctionCallExpression();
          checkFunctionExpression(exp);
        }
      }
      return TraversalProcess.CONTINUE;
    }

    @Override
    public TraversalProcess visitNode(CFANode pNode) {
      return TraversalProcess.CONTINUE;
    }

    private void checkFunctionExpression(CFunctionCallExpression exp) {
      String fName = exp.getFunctionNameExpression().toString();
      if (threadCreate.contains(fName) || fName.equals(threadCreateN)) {
        List<CExpression> args = exp.getParameterExpressions();
        if (args.size() != 4) {
          throw new UnsupportedOperationException("More arguments expected: " + exp);
        }

        CExpression calledFunction = args.get(2);
        CIdExpression functionNameExpression = getFunctionName(calledFunction);
        String newThreadName = functionNameExpression.getName();
        threadedFunctions.add(newThreadName);
      }
    }

    private CIdExpression getFunctionName(CExpression fName) {
      if (fName instanceof CIdExpression) {
        return (CIdExpression) fName;
      } else if (fName instanceof CUnaryExpression) {
        return getFunctionName(((CUnaryExpression) fName).getOperand());
      } else if (fName instanceof CCastExpression) {
        return getFunctionName(((CCastExpression) fName).getOperand());
      } else {
        throw new UnsupportedOperationException(
            "Unsupported expression in pthread_create: " + fName);
      }
    }

    public Collection<String> getThreadedFunctions() {
      return threadedFunctions;
    }
  }

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
    class FunctionSize implements Comparator<String> {
      private final ParseResult parseResult;
      public FunctionSize(final ParseResult pr) {
        parseResult = pr;
      }
      @Override
      public int compare(String pArg0, String pArg1) {
        return parseResult.getCFANodes().get(pArg0).size()
            - parseResult.getCFANodes().get(pArg1).size();
      }
    }

    List<String> answer = new ArrayList<>(pParseResult.getFunctions().keySet());
    answer.removeAll(whitelist);

    if (enableThreadOperationsInstrumentation) {
      final ThreadFinder threadVisitor = new ThreadFinder();

      for (FunctionEntryNode functionStartNode : pParseResult.getFunctions().values()) {
        CFATraversal.dfs().traverseOnce(functionStartNode, threadVisitor);
      }

      answer.removeAll(threadVisitor.getThreadedFunctions());
    }

    Collections.sort(answer, new FunctionSize(pParseResult));
    return answer;
  }

  @Override
  protected Pair<FunctionEntryNode, SortedSet<CFANode>> removeObject(
      ParseResult pParseResult, String functionName) {
    FunctionEntryNode entry = pParseResult.getFunctions().get(functionName);
    SortedSet<CFANode> nodes = new TreeSet<>(pParseResult.getCFANodes().get(functionName));
    logger.logf(
        Level.FINE, "removing %s (entry is %s, %d nodes)", functionName, entry, nodes.size());
    pParseResult.getCFANodes().removeAll(functionName);
    pParseResult.getFunctions().remove(functionName);

    return Pair.of(entry, nodes);
  }

  @Override
  protected void returnObject(
      ParseResult pParseResult, Pair<FunctionEntryNode, SortedSet<CFANode>> pRollbackInfo) {
    String functionName = pRollbackInfo.getFirst().getFunctionName();
    logger.logf(
        Level.FINE,
        "returning %s (entry is %s, %d nodes)",
        functionName,
        pRollbackInfo.getFirst(),
        pRollbackInfo.getSecond().size());
    pParseResult.getCFANodes().putAll(functionName, pRollbackInfo.getSecond());
    pParseResult.getFunctions().put(functionName, pRollbackInfo.getFirst());
  }
}
