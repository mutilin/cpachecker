// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2021 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.cpa.threadabstraction;

import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.core.defaults.AbstractCPA;
import org.sosy_lab.cpachecker.core.defaults.AutomaticCPAFactory;
import org.sosy_lab.cpachecker.core.defaults.DelegateAbstractDomain;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.ApplyOperator;
import org.sosy_lab.cpachecker.core.interfaces.CPAFactory;
import org.sosy_lab.cpachecker.core.interfaces.ConfigurableProgramAnalysisTM;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.core.interfaces.StateSpacePartition;

public class ThreadAbstractionCPA extends AbstractCPA implements ConfigurableProgramAnalysisTM {

  public ThreadAbstractionCPA(Configuration pConfig) throws InvalidConfigurationException {
    super(
        "sep",
        "sep",
        DelegateAbstractDomain.<ThreadAbstractionState>getInstance(),
        new ThreadAbstractionTransferRelation(pConfig));
  }

  public static CPAFactory factory() {
    return AutomaticCPAFactory.forType(ThreadAbstractionCPA.class);
  }

  @Override
  public ApplyOperator getApplyOperator() {
    return new ThreadAbstractionApplyOperator();
  }

  @Override
  public AbstractState getInitialState(CFANode pNode, StateSpacePartition pPartition)
      throws InterruptedException {
    return ThreadAbstractionState.emptyState();
  }

  @Override
  public Precision getInitialPrecision(CFANode node, StateSpacePartition partition)
      throws InterruptedException {
    return new ThreadAbstractionPrecision();
  }
}
