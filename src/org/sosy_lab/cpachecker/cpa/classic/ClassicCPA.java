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
package org.sosy_lab.cpachecker.cpa.classic;

import java.util.Collections;
import javax.annotation.Nullable;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.ConfigurableProgramAnalysis;
import org.sosy_lab.cpachecker.core.interfaces.ConfigurableProgramAnalysisWithPartialStates;
import org.sosy_lab.cpachecker.core.interfaces.FrontierOperator;
import org.sosy_lab.cpachecker.core.interfaces.MergeOperator;
import org.sosy_lab.cpachecker.core.interfaces.PrecisionAdjustment;
import org.sosy_lab.cpachecker.core.interfaces.StateSpacePartition;
import org.sosy_lab.cpachecker.core.interfaces.StopOperator;
import org.sosy_lab.cpachecker.core.interfaces.TransferRelationWithPartialStates;
import org.sosy_lab.cpachecker.core.interfaces.UpdateOperator;
import org.sosy_lab.cpachecker.core.interfaces.WrapperCPA;

public class ClassicCPA implements ConfigurableProgramAnalysisWithPartialStates, WrapperCPA {

  private final ConfigurableProgramAnalysis wrappedCPA;

  public ClassicCPA(ConfigurableProgramAnalysis pCpa) {
    wrappedCPA = pCpa;
  }

  @Override
  public FrontierOperator getFrontierOperator() {
    return (reached, state, precision) -> reached.add(state, precision);
  }

  @Override
  public UpdateOperator getUpdateOperator() {
    return (reached, remove, add) -> {
      reached.removeAll(remove);
      reached.addAll(add);
    };
  }

  @Override
  public TransferRelationWithPartialStates getTransferRelation() {
    return new ClassicTransferRelation(wrappedCPA.getTransferRelation());
  }

  @Override
  public PrecisionAdjustment getPrecisionAdjustment() {
    return wrappedCPA.getPrecisionAdjustment();
  }

  @Override
  public MergeOperator getMergeOperator() {
    return wrappedCPA.getMergeOperator();
  }

  @Override
  public StopOperator getStopOperator() {
    return wrappedCPA.getStopOperator();
  }

  @Override
  public AbstractState getInitialState(CFANode pNode, StateSpacePartition pPartition) throws InterruptedException {
    return wrappedCPA.getInitialState(pNode, pPartition);
  }

  @Override
  public @Nullable <T extends ConfigurableProgramAnalysis> T retrieveWrappedCpa(Class<T> pType) {
    if (wrappedCPA instanceof WrapperCPA) {
      return ((WrapperCPA) wrappedCPA).retrieveWrappedCpa(pType);
    }
    return null;
  }

  @Override
  public Iterable<ConfigurableProgramAnalysis> getWrappedCPAs() {
    return Collections.singleton(wrappedCPA);
  }

}
