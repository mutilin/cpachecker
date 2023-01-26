// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2022 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.cpa.signal;

import java.util.Collection;
import java.util.Collections;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CDeclarationEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CFunctionCallEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CStatementEdge;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.core.interfaces.Statistics;
import org.sosy_lab.cpachecker.core.interfaces.TransferRelation;
import org.sosy_lab.cpachecker.cpa.usage.WitnessNoteProvider;
import org.sosy_lab.cpachecker.exceptions.CPATransferException;

@Options(prefix = "cpa.signal")
public class SignalTransferRelation implements TransferRelation, WitnessNoteProvider {
  enum VisitorType {
    Empty
  }

  @Option(secure = true, description = "type of visitor", name = "type")
  private VisitorType vType = VisitorType.Empty;

  private final SignalStatistics stats = new SignalStatistics();
  private final CFAVisitor visitor;

  public SignalTransferRelation(
      Configuration pConfig,
      @SuppressWarnings("unused") LogManager pLogger)
      throws InvalidConfigurationException {
    pConfig.inject(this);
    switch (vType) {
      case Empty:
        visitor = new EmptyVisitor();
        break;

      default:
        throw new InvalidConfigurationException("Unknown visitor type: " + vType);
    }
  }

  @Override
  public Collection<? extends AbstractState>
      getAbstractSuccessors(AbstractState pState, Precision pPrecision)
          throws CPATransferException, InterruptedException {
    return null;
  }

  @Override
  public Collection<? extends AbstractState>
      getAbstractSuccessorsForEdge(AbstractState pState, Precision pPrecision, CFAEdge pCfaEdge)
          throws CPATransferException, InterruptedException {

    stats.transfer.start();
    SignalState tState = (SignalState) pState;

    try {
      SignalEffect effect = getEffectForEdge(pCfaEdge);
      SignalState newState = effect.apply(tState);

      stats.trackedSignals.setNextValue(newState.getSignals().size());
      return Collections.singleton(newState);
    } finally {
      stats.transfer.stop();
    }
  }

  private final SignalEffect getEffectForEdge(CFAEdge pCfaEdge) {
    switch (pCfaEdge.getEdgeType()) {
      case DeclarationEdge:
        return visitor.visitDeclarationEdge((CDeclarationEdge) pCfaEdge);

      case FunctionCallEdge:
        return visitor.visitFunctionCallEdge((CFunctionCallEdge) pCfaEdge);

      case StatementEdge:
        return visitor.visitStatementEdge((CStatementEdge) pCfaEdge);

      default:
        return EmptySignalEffect.getInstance();
    }
  }

  Statistics getStatistics() {
    return stats;
  }

  @Override
  public String getNoteForEdge(CFAEdge pEdge) {
    SignalEffect effect = getEffectForEdge(pEdge);

    if (effect != EmptySignalEffect.getInstance()) {
      return pEdge.getDescription();
    }
    return "";
  }

}
