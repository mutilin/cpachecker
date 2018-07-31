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
package org.sosy_lab.cpachecker.cpa.inferenceobjects;

import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.ConfigurableProgramAnalysisWithPartialStates;
import org.sosy_lab.cpachecker.core.interfaces.FrontierOperator;
import org.sosy_lab.cpachecker.core.interfaces.MergeOperator;
import org.sosy_lab.cpachecker.core.interfaces.StateSpacePartition;
import org.sosy_lab.cpachecker.core.interfaces.StopOperator;
import org.sosy_lab.cpachecker.core.interfaces.TransferRelationWithPartialStates;
import org.sosy_lab.cpachecker.core.interfaces.UpdateOperator;


public class InferenceObjectsCPA implements ConfigurableProgramAnalysisWithPartialStates {



  @Override
  public TransferRelationWithPartialStates getTransferRelation() {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public MergeOperator getMergeOperator() {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public StopOperator getStopOperator() {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public AbstractState getInitialState(CFANode pNode, StateSpacePartition pPartition) throws InterruptedException {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public FrontierOperator getFrontierOperator() {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public UpdateOperator getUpdateOperator() {
    // TODO Auto-generated method stub
    return null;
  }

}
