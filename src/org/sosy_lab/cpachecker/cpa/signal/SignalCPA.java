// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2022 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.cpa.signal;

import java.util.Collection;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.core.defaults.AbstractCPA;
import org.sosy_lab.cpachecker.core.defaults.AutomaticCPAFactory;
import org.sosy_lab.cpachecker.core.defaults.DelegateAbstractDomain;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.CPAFactory;
import org.sosy_lab.cpachecker.core.interfaces.ConfigurableProgramAnalysisTM;
import org.sosy_lab.cpachecker.core.interfaces.ConfigurableProgramAnalysisWithBAM;
import org.sosy_lab.cpachecker.core.interfaces.Reducer;
import org.sosy_lab.cpachecker.core.interfaces.StateSpacePartition;
import org.sosy_lab.cpachecker.core.interfaces.Statistics;
import org.sosy_lab.cpachecker.core.interfaces.StatisticsProvider;
import org.sosy_lab.cpachecker.cpa.lock.AbstractLockState;

@Options(prefix = "cpa.signal")
public class SignalCPA extends AbstractCPA
    implements ConfigurableProgramAnalysisTM, StatisticsProvider,
    ConfigurableProgramAnalysisWithBAM {

  @Option(secure = true, description = "type of state", name = "deadlockState")
  private boolean deadlock = true;

  public static CPAFactory factory() {
    return AutomaticCPAFactory.forType(SignalCPA.class);
  }

  private SignalCPA(Configuration config, LogManager pLogger) throws InvalidConfigurationException {
    super(
        "sep",
        "sep",
        DelegateAbstractDomain.<AbstractLockState>getInstance(),
        new SignalTransferRelation(config, pLogger));
    config.inject(this);
  }

  @Override
  public AbstractState getInitialState(CFANode pNode, StateSpacePartition pPartition)
      throws InterruptedException {
    if (deadlock) {
      return SignalState.createInitialState(
        (SignalStatistics) ((SignalTransferRelation) getTransferRelation()).getStatistics());
    } else {
      return InversionSignalState.createInitialState(
          (SignalStatistics) ((SignalTransferRelation) getTransferRelation()).getStatistics());

    }
  }

  @Override
  public void collectStatistics(Collection<Statistics> pStatsCollection) {
    pStatsCollection.add(((SignalTransferRelation) getTransferRelation()).getStatistics());
  }

  @Override
  public Reducer getReducer() throws InvalidConfigurationException {
    return new SignalReducer();
  }
}
