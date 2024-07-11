// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2007-2020 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.cpa.thread;

import com.google.common.base.Preconditions;
import java.util.Collection;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.core.defaults.AbstractCPA;
import org.sosy_lab.cpachecker.core.defaults.AutomaticCPAFactory;
import org.sosy_lab.cpachecker.core.defaults.DelegateAbstractDomain;
import org.sosy_lab.cpachecker.core.defaults.NoOpReducer;
import org.sosy_lab.cpachecker.core.defaults.SingletonPrecision;
import org.sosy_lab.cpachecker.core.defaults.StaticPrecisionAdjustment;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.ApplyOperator;
import org.sosy_lab.cpachecker.core.interfaces.CPAFactory;
import org.sosy_lab.cpachecker.core.interfaces.ConfigurableProgramAnalysisTM;
import org.sosy_lab.cpachecker.core.interfaces.ConfigurableProgramAnalysisWithBAM;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.core.interfaces.PrecisionAdjustment;
import org.sosy_lab.cpachecker.core.interfaces.Reducer;
import org.sosy_lab.cpachecker.core.interfaces.StateSpacePartition;
import org.sosy_lab.cpachecker.core.interfaces.Statistics;
import org.sosy_lab.cpachecker.core.interfaces.StatisticsProvider;
import org.sosy_lab.cpachecker.core.interfaces.TransferRelation;

@Options(prefix = "cpa.thread")
public class ThreadCPA extends AbstractCPA
    implements ConfigurableProgramAnalysisWithBAM,
        StatisticsProvider,
        ConfigurableProgramAnalysisTM {

  public enum ThreadMode {
    SIMPLE,
    ENVIRONMENT,
    BASE
  }

  public enum SelfSupport {
    SUPPORT,
    SKIP,
    FAIL
  }

  @Option(secure = true, description = "How to handle several instances of the same thread")
  private SelfSupport handleSameThreads = SelfSupport.FAIL;

  @Option(secure = true, description = "Use specific mode for thread analysis")
  private ThreadMode mode = ThreadMode.BASE;

  @Option(secure = true, description = "Use aggressive reduction mode")
  private boolean useAggressiveReduction = false;

  private final ThreadCPAStatistics threadStatistics;

  public static CPAFactory factory() {
    return AutomaticCPAFactory.forType(ThreadCPA.class);
  }

  public ThreadCPA(Configuration pConfig) throws InvalidConfigurationException {
    super("sep", "sep", DelegateAbstractDomain.<ThreadState>getInstance(), null);
    pConfig.inject(this);
    threadStatistics = new ThreadCPAStatistics();
  }

  @Override
  public PrecisionAdjustment getPrecisionAdjustment() {
    return StaticPrecisionAdjustment.getInstance();
  }

  @Override
  public AbstractState getInitialState(CFANode pNode, StateSpacePartition pPartition) {
    Preconditions.checkNotNull(pNode);
    switch (mode) {
      case SIMPLE:
        return SimpleThreadState.emptyState(handleSameThreads);
      case ENVIRONMENT:
        return ThreadTMState.emptyState(handleSameThreads);
      case BASE:
        return ThreadState.emptyState(handleSameThreads);
      default:
        throw new UnsupportedOperationException("Unexpected thread analysis mode: " + mode);
    }
  }

  @Override
  public Precision getInitialPrecision(CFANode pNode, StateSpacePartition partition) {
    return SingletonPrecision.getInstance();
  }

  @Override
  public Reducer getReducer() {
    if (useAggressiveReduction) {
      return new AgressiveThreadReducer();
    } else {
      return NoOpReducer.getInstance();
    }
  }

  @Override
  public void collectStatistics(Collection<Statistics> pStatsCollection) {
    pStatsCollection.add(threadStatistics);
  }

  @Override
  public ApplyOperator getApplyOperator() {
    switch (mode) {
      case ENVIRONMENT:
        return new ThreadTMApplyOperator();
      default:
        return new ThreadApplyOperator();
    }
  }

  @Override
  public TransferRelation getTransferRelation() {
    return new ThreadTransferRelation(handleSameThreads, threadStatistics);
  }
}
