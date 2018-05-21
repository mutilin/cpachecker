/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2014  Dirk Beyer
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
 *
 *
 *  CPAchecker web page:
 *    http://cpachecker.sosy-lab.org
 */
package org.sosy_lab.cpachecker.cpa.automaton;

import com.google.common.collect.ImmutableList;
import java.math.BigInteger;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.cfa.ast.AAstNode;
import org.sosy_lab.cpachecker.cfa.ast.AExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CAstNode;
import org.sosy_lab.cpachecker.cfa.ast.c.CExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CIdExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CIntegerLiteralExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.SubstitutingCAstNodeVisitor;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.types.c.CNumericTypes;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;

class AutomatonExpressionArguments {

  private Map<String, AutomatonVariable> automatonVariables;
  // Variables that are only valid for one transition ($1,$2,...)
  // these will be set in a MATCH statement, and are erased when the transitions actions are
  // executed.
  private Map<Integer, AAstNode> transitionVariables = new HashMap<>();
  private List<AbstractState> abstractStates;
  private AutomatonState state;
  private CFAEdge cfaEdge;
  private LogManager logger;

  /**
   * In this String all print messages of the Transition are collected.
   * They are logged (INFO-level) together at the end of the transition actions.
   */
  private String transitionLogMessages = "";

  // the pattern \$\$\d+ matches Expressions like $$x $$y23rinnksd $$AutomatonVar (all terminated by a non-word-character)
  static Pattern AUTOMATON_VARS_PATTERN = Pattern.compile("\\$\\$[a-zA-Z]\\w*");
  // the pattern \$\d+ matches Expressions like $1 $2 $3 $201
  // If this pattern is changed the pattern in AutomatonASTcomparison should be changed too!
  static Pattern TRANSITION_VARS_PATTERN = Pattern.compile("\\$\\d+");

  AutomatonExpressionArguments(AutomatonState pState,
      Map<String, AutomatonVariable> pAutomatonVariables,
      List<AbstractState> pAbstractStates, CFAEdge pCfaEdge,
      LogManager pLogger) {
    super();
    if (pAutomatonVariables == null) {
      automatonVariables = Collections.emptyMap();
    } else {
      automatonVariables = pAutomatonVariables;
    }
    if (pAbstractStates == null) {
      abstractStates = Collections.emptyList();
    } else {
      abstractStates = pAbstractStates;
    }
    cfaEdge = pCfaEdge;
    logger = pLogger;
    state = pState;
  }

  void setAutomatonVariables(Map<String, AutomatonVariable> pAutomatonVariables) {
    automatonVariables = pAutomatonVariables;
  }

  Map<String, AutomatonVariable> getAutomatonVariables() {
    return automatonVariables;
  }

  List<AbstractState> getAbstractStates() {
    return abstractStates;
  }

  CFAEdge getCfaEdge() {
    return cfaEdge;
  }

  LogManager getLogger() {
    return logger;
  }
  void appendToLogMessage(String message) {
    this.transitionLogMessages = transitionLogMessages  + message;
  }
  void appendToLogMessage(int message) {
    this.transitionLogMessages = transitionLogMessages  + message;
  }
  String getLogMessage() {
    return transitionLogMessages;
  }
  public void clearLogMessage() {
    transitionLogMessages = "";
  }

  void clearTransitionVariables() {
    this.transitionVariables.clear();
  }

  AAstNode getTransitionVariable(int key) {
    // this is the variable adressed with $<key> in the automaton definition
    return this.transitionVariables.get(Integer.valueOf(key));
  }

  void putTransitionVariable(int key, AAstNode value) {
    this.transitionVariables.put(key, value);
  }

  /**
   * This method replaces all references to
   * 1. AutomatonVariables (referenced by $$<Name-of-Variable>)
   * 2. TransitionVariables (referenced by $<Number-of-Variable>)
   * with the values of the variables.
   * If the variable is not found the function returns null.
   */
  String replaceVariables(String pSourceString) {

    // replace references to Transition Variables
    Matcher matcher = AutomatonExpressionArguments.TRANSITION_VARS_PATTERN.matcher(pSourceString);
    StringBuffer result = new StringBuffer();
    while (matcher.find()) {
      matcher.appendReplacement(result, "");
      String key = matcher.group().substring(1); // matched string startswith $
      try {
        int varKey = Integer.parseInt(key);
        String var = this.getTransitionVariable(varKey).toASTString();
        if (var == null) {
          // this variable has not been set.
          this.getLogger().log(Level.WARNING, "could not replace the transition variable $" + varKey + " (not found).");
          return null;
        } else {
          result.append(var);
        }
      } catch (NumberFormatException e) {
        this.getLogger().log(Level.WARNING, "could not parse the int in " + matcher.group() + " , leaving it untouched");
        result.append(matcher.group());
      }
    }
    matcher.appendTail(result);

    // replace references to automaton Variables
    matcher = AutomatonExpressionArguments.AUTOMATON_VARS_PATTERN.matcher(result.toString());
    result = new StringBuffer();
    while (matcher.find()) {
      matcher.appendReplacement(result, "");
      String varName =  matcher.group().substring(2); // matched string starts with $$
      AutomatonVariable variable = this.getAutomatonVariables().get(varName);
      if (variable == null) {
        // this variable has not been set.
        this.getLogger().log(Level.WARNING, "could not replace the Automaton variable reference " + varName + " (not found).");
        return null;
      } else {
        result.append(variable.getValue());
      }
    }
    matcher.appendTail(result);
    return result.toString();
  }

  public AutomatonState getState() {
    return state;
  }

  public Map<Integer, AAstNode> getTransitionVariables() {
    return this.transitionVariables;
  }

  public void putTransitionVariables(Map<Integer, AAstNode> pTransitionVariables) {
    this.transitionVariables.putAll(pTransitionVariables);
  }

  public ImmutableList<AExpression> instantiateAssumtions(ImmutableList<AExpression> pAssumptions) {
    ImmutableList.Builder<AExpression> builder = ImmutableList.builder();
    SubstitutingCAstNodeVisitor visitor =
        new SubstitutingCAstNodeVisitor(
            new SubstitutingCAstNodeVisitor.SubstituteProvider() {
              @Override
              public CAstNode findSubstitute(CAstNode pNode) {
                if ((pNode instanceof CIdExpression)) {
                  CIdExpression exp = (CIdExpression) pNode;
                  String name = exp.getName();
                  Matcher matcher =
                      AutomatonExpressionArguments.AUTOMATON_VARS_PATTERN.matcher(name);
                  if (matcher.find()) {
                    // Take value of internal automata variable ($$<variable>).
                    String varName = matcher.group().substring(2);
                    AutomatonVariable variable = automatonVariables.get(varName);
                    if (variable != null) {
                      return new CIntegerLiteralExpression(
                          pNode.getFileLocation(),
                          CNumericTypes.INT,
                          BigInteger.valueOf(variable.getValue()));
                    }
                  }
                  matcher = AutomatonExpressionArguments.TRANSITION_VARS_PATTERN.matcher(name);
                  if (matcher.find()) {
                    // Take name of variable, which was referenced in transition assumption ($<id>).
                    String varId = matcher.group().substring(1);
                    try {
                      return (CAstNode) transitionVariables.get(Integer.parseInt(varId));
                    } catch (NumberFormatException e) {
                      logger.log(
                          Level.WARNING, "could not parse the int in transition variable " + varId);
                    }
                  }
                }
                return null;
              }
            });
    for (AExpression expr : pAssumptions) {
      if ((expr instanceof CExpression)) {
        CExpression substitutedExpr = (CExpression) ((CExpression) expr).accept(visitor);
        builder.add(substitutedExpr);
      } else {
        logger.log(Level.WARNING, "could not instantiate transition assumption");
        builder.add(expr);
      }
    }
    return builder.build();
  }
}
