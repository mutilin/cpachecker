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

import com.google.common.base.Preconditions;
import java.util.ArrayList;
import java.util.Collection;
import org.sosy_lab.cpachecker.core.algorithm.DefaultWaitlistElement;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.core.interfaces.TransferRelation;
import org.sosy_lab.cpachecker.core.interfaces.TransferRelationWithPartialStates;
import org.sosy_lab.cpachecker.core.interfaces.WaitlistElement;
import org.sosy_lab.cpachecker.exceptions.CPATransferException;
import org.sosy_lab.cpachecker.util.Pair;


public class ClassicTransferRelation implements TransferRelationWithPartialStates {

  private final TransferRelation transferRelation;

  public ClassicTransferRelation(TransferRelation pTransfer) {
    transferRelation = pTransfer;
  }

  @Override
  public Collection<Pair<? extends AbstractState, ? extends Precision>> getAbstractSuccessors(WaitlistElement pElement) throws CPATransferException, InterruptedException {
    //TODO Array?
    Preconditions.checkArgument(pElement instanceof DefaultWaitlistElement);

    Collection<Pair<? extends AbstractState, ? extends Precision>> result = new ArrayList<>();
    DefaultWaitlistElement element = (DefaultWaitlistElement) pElement;
    AbstractState state = element.getAbstractState();
    Precision prec = element.getPrecision();
    Collection<? extends AbstractState> successors =
        transferRelation.getAbstractSuccessors(state, prec);
    successors.forEach(s -> result.add(Pair.of(s, prec)));

    return result;
  }

}
