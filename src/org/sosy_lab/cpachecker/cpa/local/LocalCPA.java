// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2007-2020 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.cpa.local;

import com.google.common.collect.ImmutableSet;
import java.util.Collection;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.cfa.CFA;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.core.defaults.AutomaticCPAFactory;
import org.sosy_lab.cpachecker.core.defaults.DelegateAbstractDomain;
import org.sosy_lab.cpachecker.core.defaults.MergeJoinOperator;
import org.sosy_lab.cpachecker.core.defaults.MergeSepOperator;
import org.sosy_lab.cpachecker.core.defaults.SingletonPrecision;
import org.sosy_lab.cpachecker.core.defaults.StopSepOperator;
import org.sosy_lab.cpachecker.core.interfaces.AbstractDomain;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.CPAFactory;
import org.sosy_lab.cpachecker.core.interfaces.ConfigurableProgramAnalysisWithBAM;
import org.sosy_lab.cpachecker.core.interfaces.MergeOperator;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.core.interfaces.PrecisionAdjustment;
import org.sosy_lab.cpachecker.core.interfaces.Reducer;
import org.sosy_lab.cpachecker.core.interfaces.StateSpacePartition;
import org.sosy_lab.cpachecker.core.interfaces.Statistics;
import org.sosy_lab.cpachecker.core.interfaces.StatisticsProvider;
import org.sosy_lab.cpachecker.core.interfaces.StopOperator;
import org.sosy_lab.cpachecker.core.interfaces.TransferRelation;
import org.sosy_lab.cpachecker.cpa.local.LocalState.LocalStateComplete;

@Options(prefix = "cpa.local")
public class LocalCPA implements ConfigurableProgramAnalysisWithBAM, StatisticsProvider {
  @Option(name = "localvariables", description = "variables, which are always local", secure = true)
  private ImmutableSet<String> localVariables = ImmutableSet.of();

  @Option(description = "remove information from outer functions", secure = true)
  private boolean cleanFunctionStacks = true;

  @Option(description = "Use refinement procedure", secure = true)
  private boolean refinement = true;

  @Option(
      secure = true,
      name = "merge",
      values = {"SEP", "JOIN"},
      toUppercase = true,
      description = "which merge operator to use for local cpa")
  private String mergeType = "SEP";

  private final LocalTransferRelation transfer;

  public static CPAFactory factory() {
    return AutomaticCPAFactory.forType(LocalCPA.class);
  }

  private LocalCPA(CFA pCfa, LogManager pLogger, Configuration pConfig)
      throws InvalidConfigurationException {
    pConfig.inject(this);
    transfer = new LocalTransferRelation(pConfig, pLogger, pCfa.getVarClassification());
  }

  @Override
  public AbstractState getInitialState(CFANode pNode, StateSpacePartition p) {
    if (cleanFunctionStacks) {
      return LocalState.createInitialLocalState(localVariables);
    } else {
      return LocalStateComplete.createInitialLocalStateComplete(localVariables);
    }
  }

  @Override
  public Reducer getReducer() {
    return new LocalReducer();
  }

  @Override
  public void collectStatistics(Collection<Statistics> pStatsCollection) {
    pStatsCollection.add(transfer.getStatistics());
  }

  @Override
  public Precision getInitialPrecision(CFANode node, StateSpacePartition partition)
      throws InterruptedException {
    if (refinement) {
      return new LocalPrecision(ImmutableSet.of());
    }
    return SingletonPrecision.getInstance();
  }

  @Override
  public PrecisionAdjustment getPrecisionAdjustment() {
    return new LocalPrecisionAdjustment();
  }

  @Override
  public AbstractDomain getAbstractDomain() {
    return DelegateAbstractDomain.<LocalState>getInstance();
  }

  @Override
  public TransferRelation getTransferRelation() {
    return transfer;
  }

  @Override
  public MergeOperator getMergeOperator() {
    switch (mergeType) {
      case "SEP":
        return MergeSepOperator.getInstance();

      case "JOIN":
        return new MergeJoinOperator(getAbstractDomain());

      default:
        throw new AssertionError("unknown merge operator");
    }
  }

  @Override
  public StopOperator getStopOperator() {
    return new StopSepOperator(getAbstractDomain());
  }
}
