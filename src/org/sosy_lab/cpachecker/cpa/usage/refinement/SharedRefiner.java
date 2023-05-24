// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2007-2020 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.cpa.usage.refinement;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.cfa.model.c.CFunctionCallEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CFunctionReturnEdge;
import org.sosy_lab.cpachecker.core.defaults.SingletonPrecision;
import org.sosy_lab.cpachecker.cpa.local.LocalPrecision;
import org.sosy_lab.cpachecker.cpa.local.LocalState;
import org.sosy_lab.cpachecker.cpa.local.LocalState.DataType;
import org.sosy_lab.cpachecker.cpa.local.LocalTransferRelation;
import org.sosy_lab.cpachecker.cpa.usage.UsageInfo;
import org.sosy_lab.cpachecker.exceptions.CPAException;
import org.sosy_lab.cpachecker.exceptions.CPATransferException;
import org.sosy_lab.cpachecker.util.AbstractStates;
import org.sosy_lab.cpachecker.util.Pair;
import org.sosy_lab.cpachecker.util.identifiers.AbstractIdentifier;
import org.sosy_lab.cpachecker.util.statistics.StatCounter;
import org.sosy_lab.cpachecker.util.statistics.StatisticsWriter;

public class SharedRefiner extends GenericSinglePathRefiner {

  private LocalTransferRelation transferRelation;

  //Debug counter
  private StatCounter counter = new StatCounter("Number of cases with empty successors");
  //private final StatInt totalFalseConditions = new StatInt(StatKind.COUNT, "Number of false conditions that were detected by SharedRefiner");
  private StatCounter numOfFalseResults = new StatCounter("Number of false results");

  public SharedRefiner(ConfigurableRefinementBlock<Pair<ExtendedARGPath, ExtendedARGPath>> pWrapper, LocalTransferRelation RelationForSharedRefiner) {
    super(pWrapper);
    transferRelation = RelationForSharedRefiner;
  }

  @Override
  protected RefinementResult call(ExtendedARGPath pPath) throws CPAException, InterruptedException {
    RefinementResult result = RefinementResult.createTrue();
    List<CFAEdge> edges  = pPath.getFullPath();
    SingletonPrecision emptyPrecision = SingletonPrecision.getInstance();

    LocalState lastState = AbstractStates.extractStateByType(pPath.getLastState(), LocalState.class);
    LocalState initialState = lastState.createInitialLocalState();

    Collection<LocalState> successors = Collections.singleton(initialState);
    UsageInfo sharedUsage = pPath.getUsageInfo();
    AbstractIdentifier usageId = pPath.getUsageInfo().getId();
    List<Pair<LocalState, CFAEdge>> fullPath = new ArrayList<>();

    for (CFAEdge edge : edges) {
      if (successors.isEmpty()) {
        // Strange situation
        counter.inc();
        break;
      }
      assert (successors.size() == 1);
      LocalState usageState = Iterables.getOnlyElement(successors);
      assert (usageState != null);
      fullPath.add(Pair.of(usageState, edge));

      if (sharedUsage.getCFANode().equals(edge.getSuccessor())) {

        if (usageState.getType(usageId) == LocalState.DataType.LOCAL) {
          result = RefinementResult.createFalse();
          Set<AbstractIdentifier> newIds = getInterpolants(fullPath, usageId);
          LocalPrecision newPrec = new LocalPrecision(newIds);
          result.addPrecision(newPrec);
          numOfFalseResults.inc();
        }
        break;
      } else {
        //TODO Important! Final state is not a state of usage. Think about.
        successors =
            transferRelation.getAbstractSuccessorsForEdge(usageState, emptyPrecision, edge);
      }
    }

    //totalFalseConditions.setNextValue(numOfFalseResults.getValue());
    return result;
  }

  private Set<AbstractIdentifier>
      getInterpolants(List<Pair<LocalState, CFAEdge>> fullPath, AbstractIdentifier usageId)
          throws CPATransferException, InterruptedException {

    Set<AbstractIdentifier> result = new TreeSet<>();
    LocalState sucState = fullPath.get(fullPath.size() - 1).getFirst();
    AbstractIdentifier toPrec = sucState.getIdForPrecision(usageId);
    assert toPrec != null;
    result.add(toPrec);
    CFANode entryFunc = null;

    for (int i = fullPath.size() - 2; i > 0; i--) {
      LocalState prevState = fullPath.get(i).getFirst();
      CFAEdge edge = fullPath.get(i).getSecond();
      if (entryFunc != null) {
        if (edge instanceof CFunctionCallEdge && edge.getSuccessor() == entryFunc) {
          entryFunc = null;
        } else {
          // Still unrolling
          continue;
        }
      }
      if (prevState.getType(toPrec) != DataType.LOCAL) {
        sucState = fullPath.get(i + 1).getFirst();
        assert sucState.getType(toPrec) == DataType.LOCAL;

        // Check if there is more ids important
        AbstractIdentifier id = getAssignedIds(edge, toPrec, prevState);
        if (id == null) {
          assert (edge instanceof CFunctionReturnEdge);
          // need to move to entry
          entryFunc = ((CFunctionReturnEdge)edge).getFunctionEntry();
          continue;
        } else if (id == toPrec) {
          break;
        }
        result.add(id);
        toPrec = id;
      }
    }

    return result;
  }

  private AbstractIdentifier
      getAssignedIds(CFAEdge edge, AbstractIdentifier id, LocalState prev)
          throws CPATransferException, InterruptedException {

    LocalState initialState = prev.totalReset();

    if (checkStep(initialState, edge, id)) {
      // Result is independent from the state
      return id;
    }

    initialState = prev.reset();

    if (checkStep(initialState, edge, id)) {
      // Result is extracted from the function entry
      return null;
    }


    for (AbstractIdentifier checkedId : prev.getStoredIds()) {
      if (checkStep(prev.forget(ImmutableSet.of(checkedId)), edge, id)) {
        return checkedId;
      }
    }

    throw new AssertionError("Unreachable");
  }

  private boolean checkStep(LocalState state, CFAEdge edge, AbstractIdentifier id)
      throws CPATransferException, InterruptedException {

    Collection<LocalState> successors =
        transferRelation
            .getAbstractSuccessorsForEdge(state, SingletonPrecision.getInstance(), edge);
    assert successors.size() == 1;
    LocalState nextState = Iterables.getOnlyElement(successors);

    return nextState.getType(id) == DataType.LOCAL;
  }

  @Override
  protected void printAdditionalStatistics(StatisticsWriter pOut) {
    pOut.beginLevel()
      .put(counter)
      .put(numOfFalseResults);
    //pOut.println(totalFalseConditions);
  }
}
