/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2018  Dirk Beyer
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
package org.sosy_lab.cpachecker.cpa.usage;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import de.uni_freiburg.informatik.ultimate.smtinterpol.util.IdentityHashSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Level;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.cfa.ast.c.CAssignment;
import org.sosy_lab.cpachecker.cfa.ast.c.CExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CExpressionStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionCallAssignmentStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionCallExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionCallStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CInitializer;
import org.sosy_lab.cpachecker.cfa.ast.c.CInitializerExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CRightHandSide;
import org.sosy_lab.cpachecker.cfa.ast.c.CStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CVariableDeclaration;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.cfa.model.c.CAssumeEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CDeclarationEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CFunctionCallEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CReturnStatementEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CStatementEdge;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.cpa.arg.ARGState;
import org.sosy_lab.cpachecker.cpa.local.LocalState.DataType;
import org.sosy_lab.cpachecker.cpa.lock.DeadLockState;
import org.sosy_lab.cpachecker.cpa.rcucpa.RCUState;
import org.sosy_lab.cpachecker.cpa.signal.SignalState;
import org.sosy_lab.cpachecker.cpa.usage.UsageInfo.Access;
import org.sosy_lab.cpachecker.util.AbstractStates;
import org.sosy_lab.cpachecker.util.Pair;
import org.sosy_lab.cpachecker.util.identifiers.AbstractIdentifier;
import org.sosy_lab.cpachecker.util.identifiers.GeneralIdentifier;
import org.sosy_lab.cpachecker.util.identifiers.GlobalVariableIdentifier;
import org.sosy_lab.cpachecker.util.identifiers.IdentifierCreator;
import org.sosy_lab.cpachecker.util.identifiers.Identifiers;
import org.sosy_lab.cpachecker.util.identifiers.LocalVariableIdentifier;
import org.sosy_lab.cpachecker.util.identifiers.SingleIdentifier;
import org.sosy_lab.cpachecker.util.statistics.StatTimer;
import org.sosy_lab.cpachecker.util.statistics.StatisticsWriter;

@Options(prefix = "cpa.usage")
public class UsageProcessor {

  private final Map<String, BinderFunctionInfo> binderFunctionInfo;

  protected final LogManager logger;
  private final VariableSkipper varSkipper;

  private Map<CFANode, Map<GeneralIdentifier, DataType>> precision;

  private final Collection<CFANode> uselessNodes;
  private Collection<SingleIdentifier> redundantIds;

  private IdentifierCreator creator;

  StatTimer totalTimer = new StatTimer("Total time for usage processing");
  StatTimer localTimer = new StatTimer("Time for sharedness check");
  StatTimer usagePreparationTimer = new StatTimer("Time for usage preparation");
  StatTimer usageCreationTimer = new StatTimer("Time for usage creation");
  StatTimer searchingCacheTimer = new StatTimer("Time for searching in cache");

  @Option(description = "functions, which perform atomic read access", secure = true)
  private Set<String> atomicReadFunctions = ImmutableSet.of();

  @Option(description = "functions, which perform atomic write access", secure = true)
  private Set<String> atomicWriteFunctions = ImmutableSet.of();

  public UsageProcessor(
      Configuration config,
      LogManager pLogger,
      Map<CFANode, Map<GeneralIdentifier, DataType>> pPrecision,
      Map<String, BinderFunctionInfo> pBinderFunctionInfo,
      IdentifierCreator pCreator)
      throws InvalidConfigurationException {
    logger = pLogger;
    binderFunctionInfo = pBinderFunctionInfo;

    varSkipper = new VariableSkipper(config);
    precision = pPrecision;
    uselessNodes = new IdentityHashSet<>();
    creator = pCreator;
    config.inject(this, UsageProcessor.class);
  }

  public void updateRedundantUnsafes(Set<SingleIdentifier> set) {
    redundantIds = set;
  }

  public List<UsageInfo> getUsagesForState(AbstractState pState) {

    // totalTimer.start();

    ARGState argState = (ARGState) pState;
    CFANode node = AbstractStates.extractLocation(argState);

    synchronized (uselessNodes) {
      if (uselessNodes.contains(node)) {
        // totalTimer.stop();
        return ImmutableList.of();
      }
    }
    boolean resultComputed = false;
    // Not a set, as usage.equals do not consider id
    List<UsageInfo> result = new ArrayList<>();

    for (ARGState child : argState.getChildren()) {
      CFANode childNode = AbstractStates.extractLocation(child);

      if (node.hasEdgeTo(childNode)) {
        // Need the flag to avoid missed cases with applied edges: it may be traversed first,
        // so put to useless nodes ONLY if there is a normal CFA edge.
        resultComputed = true;
        CFAEdge edge = node.getEdgeTo(childNode);

        result.addAll(createUsagesForEdge(argState, child, node, edge));
        // usagePreparationTimer.stop();

      } else {
        // No edge, for example, due to BAM
        // Note, function call edge was already handled, we do not miss it
      }
    }

    if (resultComputed && result.isEmpty()) {
      synchronized (uselessNodes) {
        uselessNodes.add(node);
      }
    }
    // totalTimer.stop();
    return ImmutableList.copyOf(result);
  }

  protected Collection<UsageInfo>
      createUsagesForEdge(
          @SuppressWarnings("unused") ARGState parent,
          ARGState child,
          CFANode node,
          CFAEdge edge) {
    Collection<Pair<AbstractIdentifier, Access>> ids;
    List<UsageInfo> result = new ArrayList<>();

    // Ignore usages inside of atomic read and write functions, as they were already handled on the
    // function call
    String fName = node.getFunctionName();
    if (!atomicReadFunctions.contains(fName) && !atomicWriteFunctions.contains(fName)) {
      ids = getIdsForEdge(edge);

      // usagePreparationTimer.start();
      for (Pair<AbstractIdentifier, Access> pair : ids) {
        AbstractIdentifier id = pair.getFirst();
        // Links will be extracted as aliases later

        // searchingCacheTimer.start();
        // searchingCacheTimer.stop();
        if (!redundantIds.contains(id)) {
          createUsages(id, node, child, pair.getSecond(), result);
        }
      }
    }
    return result;
  }

  protected Collection<Pair<AbstractIdentifier, Access>>
      getIdsForEdge(CFAEdge pCfaEdge) {

    // From successor!
    String fName = pCfaEdge.getSuccessor().getFunctionName();

    switch (pCfaEdge.getEdgeType()) {
      case DeclarationEdge:
        {
          CDeclarationEdge declEdge = (CDeclarationEdge) pCfaEdge;
          return handleDeclaration(fName, declEdge);
        }

        // if edge is a statement edge, e.g. a = b + c
      case StatementEdge:
        {
          CStatementEdge statementEdge = (CStatementEdge) pCfaEdge;
          return handleStatement(fName, statementEdge.getStatement());
        }

      case AssumeEdge:
        {
        return visitStatement(
            fName,
            ((CAssumeEdge) pCfaEdge).getExpression(),
            Access.READ);
        }

      case FunctionCallEdge:
        {
          return handleFunctionCall(fName, (CFunctionCallEdge) pCfaEdge);
        }

        case ReturnStatementEdge:
        {
          return handleReturnStatement(fName, (CReturnStatementEdge) pCfaEdge);
        }

      default:
        {
        return ImmutableSet.of();
        }
    }
  }

  private Collection<Pair<AbstractIdentifier, Access>>
      handleReturnStatement(String fName, CReturnStatementEdge edge) {
    CExpression expr = edge.getExpression().orElse(null);
    if (expr != null) {
      return visitStatement(fName, expr, Access.READ);
    }
    return ImmutableSet.of();
  }

  private Collection<Pair<AbstractIdentifier, Access>>
      handleFunctionCall(String fName, CFunctionCallEdge edge) {
    CStatement statement = edge.getFunctionCall();

    if (statement instanceof CFunctionCallAssignmentStatement) {
      /*
       * a = f(b)
       */
      CFunctionCallExpression right =
          ((CFunctionCallAssignmentStatement) statement).getRightHandSide();
      CExpression variable = ((CFunctionCallAssignmentStatement) statement).getLeftHandSide();

      List<Pair<AbstractIdentifier, Access>> internalIds =
          new ArrayList<>(handleFunctionCallExpression(fName, right));

      internalIds.addAll(visitStatement(fName, variable, Access.WRITE));
      return ImmutableList.copyOf(internalIds);

    } else if (statement instanceof CFunctionCallStatement) {
      return handleFunctionCallExpression(
          fName,
          ((CFunctionCallStatement) statement).getFunctionCallExpression());
    }
    return ImmutableSet.of();
  }

  private Collection<Pair<AbstractIdentifier, Access>>
      handleDeclaration(String fName, CDeclarationEdge declEdge) {

    if (declEdge.getDeclaration().getClass() != CVariableDeclaration.class) {
      // not a variable declaration
      return ImmutableSet.of();
    }
    CVariableDeclaration decl = (CVariableDeclaration) declEdge.getDeclaration();

    if (decl.isGlobal()) {
      return ImmutableSet.of();
    }

    CInitializer init = decl.getInitializer();

    if (init == null) {
      // no assignment
      return ImmutableSet.of();
    }

    if (init instanceof CInitializerExpression) {
      CExpression initExpression = ((CInitializerExpression) init).getExpression();
      // Use EdgeType assignment for initializer expression to avoid mistakes related to expressions
      // "int CPACHECKER_TMP_0 = global;"
      return visitStatement(fName, initExpression, Access.READ);

      // We do not add usage for currently declared variable
      // It can not cause a race
    }
    return ImmutableSet.of();
  }

  private Collection<Pair<AbstractIdentifier, Access>> handleFunctionCallExpression(
      String fName,
      final CFunctionCallExpression fcExpression) {

    String functionCallName = fcExpression.getFunctionNameExpression().toASTString();

    List<Pair<AbstractIdentifier, Access>> internalIds = new ArrayList<>();

    if (atomicReadFunctions.contains(functionCallName)) {
      List<CExpression> params = fcExpression.getParameterExpressions();
      AbstractIdentifier id =
          Identifiers.createIdentifier(params.get(0), 1, fName);
      internalIds.add(Pair.of(id, Access.ATOMIC_READ));
    } else if (atomicWriteFunctions.contains(functionCallName)) {
      List<CExpression> params = fcExpression.getParameterExpressions();
      AbstractIdentifier id =
          Identifiers.createIdentifier(params.get(0), 1, fName);
      internalIds.add(Pair.of(id, Access.ATOMIC_WRITE));
    }

    if (binderFunctionInfo.containsKey(functionCallName)) {
      BinderFunctionInfo currentInfo = binderFunctionInfo.get(functionCallName);
      List<CExpression> params = fcExpression.getParameterExpressions();

      AbstractIdentifier id;

      for (int i = 0; i < params.size(); i++) {
        id = currentInfo.createParamenterIdentifier(params.get(i), i, fName);
        internalIds.add(Pair.of(id, currentInfo.getBindedAccess(i)));
      }
    } else {

      fcExpression.getParameterExpressions()
          .forEach(p -> internalIds.addAll(visitStatement(fName, p, Access.READ)));
      internalIds
          .addAll(visitStatement(fName, fcExpression.getFunctionNameExpression(), Access.READ));
    }
    return internalIds;
  }

  private Collection<Pair<AbstractIdentifier, Access>>
      handleStatement(String fName, final CStatement pStatement) {

    if (pStatement instanceof CAssignment) {
      // assignment like "a = b" or "a = foo()"
      CAssignment assignment = (CAssignment) pStatement;
      CExpression left = assignment.getLeftHandSide();
      CRightHandSide right = assignment.getRightHandSide();
      List<Pair<AbstractIdentifier, Access>> internalIds = new ArrayList<>();

      if (right instanceof CExpression) {
        internalIds.addAll(visitStatement(fName, (CExpression) right, Access.READ));

      } else if (right instanceof CFunctionCallExpression) {
        internalIds.addAll(handleFunctionCallExpression(fName, (CFunctionCallExpression) right));
      }
      internalIds.addAll(visitStatement(fName, left, Access.WRITE));
      return ImmutableList.copyOf(internalIds);

    } else if (pStatement instanceof CFunctionCallStatement) {
      return handleFunctionCallExpression(
          fName,
          ((CFunctionCallStatement) pStatement).getFunctionCallExpression());

    } else if (pStatement instanceof CExpressionStatement) {
      return visitStatement(
          fName,
          ((CExpressionStatement) pStatement).getExpression(),
          Access.WRITE);
    }
    return ImmutableSet.of();
  }

  private Collection<Pair<AbstractIdentifier, Access>>
      visitStatement(String fName,
      final CExpression expression,
      final Access access) {
    // To avoid synchronization
    IdentifierCreator localCreator = creator.copy();
    localCreator.setCurrentFunction(fName);
    ExpressionHandler handler = new ExpressionHandler(access, fName, varSkipper, localCreator);
    expression.accept(handler);

    return handler.getProcessedExpressions();
  }

  protected void
      createUsages(
          AbstractIdentifier pId,
          CFANode pNode,
          AbstractState pChild,
          Access pAccess,
          List<UsageInfo> result) {

    // TODO looks like a hack
    if (pId instanceof SingleIdentifier) {
      SingleIdentifier singleId = (SingleIdentifier) pId;
      if (singleId.getName().contains("CPAchecker_TMP")) {
        RCUState rcuState = AbstractStates.extractStateByType(pChild, RCUState.class);
        if (rcuState != null) {
          singleId = (SingleIdentifier) rcuState.getNonTemporaryId(singleId);
        }
      }
    }

    Iterable<AliasInfoProvider> providers =
        AbstractStates.asIterable(pChild).filter(AliasInfoProvider.class);
    Set<AbstractIdentifier> aliases = new TreeSet<>();

    aliases.add(pId);
    for (AliasInfoProvider provider : providers) {
      aliases.addAll(provider.getAllPossibleAliases(pId));
    }

    for (AliasInfoProvider provider : providers) {
      provider.filterAliases(pId, aliases);
    }

    for (AbstractIdentifier aliasId : aliases) {
      createUsageAndAdd(aliasId, pNode, pChild, pAccess, result);
    }
  }

  private void createUsageAndAdd(
      AbstractIdentifier pId,
      CFANode pNode,
      AbstractState pChild,
      Access pAccess,
      List<UsageInfo> result) {

    if (pId instanceof LocalVariableIdentifier && !pId.isDereferenced()) {
      return;
    }

    // usageCreationTimer.start();
    UsageInfo usage = UsageInfo.createUsageInfo(pAccess, pChild, pId);
    // usageCreationTimer.stop();

    // Precise information, using results of shared analysis
    if (!usage.isRelevant()) {
      return;
    }

    // localTimer.start();
    Map<GeneralIdentifier, DataType> localInfo = precision.get(pNode);
    if (localInfo == null) {
      // No preset info, but there may be runtime info
      localInfo = new HashMap<>();
    }

    SingleIdentifier singleId = usage.getId();
    GeneralIdentifier gId = singleId.getGeneralId();
    if (localInfo.get(gId) == DataType.LOCAL) {
      logger.log(
          Level.FINER,
          singleId + " is considered to be local, so it wasn't add to statistics");
      // localTimer.stop();
      return;
    } else {

      Iterable<LocalInfoProvider> itStates =
          AbstractStates.asIterable(pChild).filter(LocalInfoProvider.class);
      boolean isLocal = false;
      boolean isGlobal = false;

      for (AbstractIdentifier id : singleId.getComposedIdentifiers()) {
        if (id instanceof SingleIdentifier) {
          GeneralIdentifier gcId = ((SingleIdentifier) id).getGeneralId();
          DataType type = localInfo.get(gcId);
          if (type == DataType.GLOBAL) {
            // Add global var to statistics in any case
            isGlobal = true;
            break;
          } else if (type == DataType.LOCAL) {
            isLocal = true;
          }
          for (LocalInfoProvider state : itStates) {
            isLocal |= state.isLocal(gcId);
          }
        }
      }
      for (LocalInfoProvider state : itStates) {
        isLocal |= state.isLocal(gId);
      }
      if (isLocal && !isGlobal) {
        logger.log(
            Level.FINER,
            singleId + " is supposed to be local, so it wasn't add to statistics");
        // localTimer.stop();
        return;
      }
    }

    // localTimer.stop();
    logger.log(Level.FINER, "Add " + usage + " to unsafe statistics");

    result.add(usage);
  }

  public void printStatistics(StatisticsWriter pWriter) {
    pWriter.put(totalTimer)
        .beginLevel()
        .put(usagePreparationTimer)
        .beginLevel()
        .put(searchingCacheTimer)
        .put(usageCreationTimer)
        .put(localTimer)
        .endLevel()
        .put("Number of useless nodes", uselessNodes.size());
  }

  public static class DeadLockProcessor extends UsageProcessor {

    public static final GlobalVariableIdentifier deadlockId =
        new GlobalVariableIdentifier("lock", null, 0);

    public DeadLockProcessor(
        Configuration pConfig,
        LogManager pLogger,
        Map<CFANode, Map<GeneralIdentifier, DataType>> pPrecision,
        Map<String, BinderFunctionInfo> pBinderFunctionInfo,
        IdentifierCreator pCreator)
        throws InvalidConfigurationException {
      super(pConfig, pLogger, pPrecision, pBinderFunctionInfo, pCreator);
    }

    @Override
    protected Collection<UsageInfo>
        createUsagesForEdge(ARGState parent, ARGState child, CFANode node, CFAEdge edge) {

      DeadLockState pLocks = AbstractStates.extractStateByType(parent, DeadLockState.class);
      DeadLockState cLocks = AbstractStates.extractStateByType(child, DeadLockState.class);

      if (pLocks == null || cLocks == null) {
        logger.log(
            Level.SEVERE,
            "Missed deadlock state. Likely, you should set cpa.lock.analysisMode=DEADLOCK");
        return ImmutableList.of();
      }

      if (cLocks.getSize() > pLocks.getSize()) {
        // we are interested only in lock acquires
        UsageInfo usage = UsageInfo.createUsageInfo(Access.WRITE, child, deadlockId);
        return ImmutableList.of(usage);
      }

      return ImmutableList.of();
    }
  }

  public static class SignalsProcessor extends UsageProcessor {

    public static final GlobalVariableIdentifier signalId =
        new GlobalVariableIdentifier("signal", null, 0);

    public SignalsProcessor(
        Configuration pConfig,
        LogManager pLogger,
        Map<CFANode, Map<GeneralIdentifier, DataType>> pPrecision,
        Map<String, BinderFunctionInfo> pBinderFunctionInfo,
        IdentifierCreator pCreator)
        throws InvalidConfigurationException {
      super(pConfig, pLogger, pPrecision, pBinderFunctionInfo, pCreator);
    }

    @Override
    protected Collection<UsageInfo>
        createUsagesForEdge(ARGState parent, ARGState child, CFANode node, CFAEdge edge) {

      SignalState pSignals = AbstractStates.extractStateByType(parent, SignalState.class);
      SignalState cSignals = AbstractStates.extractStateByType(child, SignalState.class);

      if (pSignals == null || cSignals == null) {
        logger.log(
            Level.SEVERE,
            "Missed signal state. Likely, you should add SignalCPA to your configuration");
        return ImmutableList.of();
      }

      if (cSignals.getSignals().size() > pSignals.getSignals().size()) {
        // we are interested only in signals
        UsageInfo usage = UsageInfo.createUsageInfo(Access.WRITE, child, signalId);
        return ImmutableList.of(usage);
      }

      return ImmutableList.of();

    }
  }
}
