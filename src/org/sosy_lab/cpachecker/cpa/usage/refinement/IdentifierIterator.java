// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2007-2020 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.cpa.usage.refinement;

import java.io.PrintStream;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.logging.Level;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.core.CPAcheckerResult.Result;
import org.sosy_lab.cpachecker.core.interfaces.AdjustablePrecision;
import org.sosy_lab.cpachecker.core.interfaces.ConfigurableProgramAnalysis;
import org.sosy_lab.cpachecker.core.interfaces.Refiner;
import org.sosy_lab.cpachecker.core.interfaces.StateSpacePartition;
import org.sosy_lab.cpachecker.core.interfaces.Statistics;
import org.sosy_lab.cpachecker.core.interfaces.WrapperCPA;
import org.sosy_lab.cpachecker.core.reachedset.ForwardingReachedSet;
import org.sosy_lab.cpachecker.core.reachedset.ReachedSet;
import org.sosy_lab.cpachecker.core.reachedset.UnmodifiableReachedSet;
import org.sosy_lab.cpachecker.cpa.bam.BAMTransferRelation;
import org.sosy_lab.cpachecker.cpa.predicate.BAMPredicateCPA;
import org.sosy_lab.cpachecker.cpa.predicate.BAMPredicateRefiner;
import org.sosy_lab.cpachecker.cpa.usage.UsageCPA;
import org.sosy_lab.cpachecker.cpa.usage.UsageReachedSet;
import org.sosy_lab.cpachecker.cpa.usage.storage.UsageContainer;
import org.sosy_lab.cpachecker.exceptions.CPAException;
import org.sosy_lab.cpachecker.util.AbstractStates;
import org.sosy_lab.cpachecker.util.CPAs;
import org.sosy_lab.cpachecker.util.identifiers.AbstractIdentifier;
import org.sosy_lab.cpachecker.util.statistics.StatCounter;
import org.sosy_lab.cpachecker.util.statistics.StatTimer;
import org.sosy_lab.cpachecker.util.statistics.StatisticsWriter;


@Options(prefix="cpa.usage")
public class IdentifierIterator
    extends WrappedConfigurableRefinementBlock<ReachedSet, AbstractIdentifier> implements Refiner {

  private class Stats implements Statistics {

    private final StatTimer innerRefinementTimer = new StatTimer("Time for inner refinement");
    private final StatTimer precisionTimer = new StatTimer("Time for precision operations");
    private final StatTimer finishingTimer = new StatTimer("Time for final operations");
    private final StatCounter iterations = new StatCounter("Number of refined identifiers");

    @Override
    public void printStatistics(PrintStream pOut, Result pResult, UnmodifiableReachedSet pReached) {
      StatisticsWriter writer = StatisticsWriter.writingStatisticsTo(pOut);
      writer.put(innerRefinementTimer).put(precisionTimer).put(finishingTimer).put(iterations);
      IdentifierIterator.this.printStatistics(writer);
    }

    @Override
    public String getName() {
      return "UsageStatisticsRefiner";
    }

  }

  private final ConfigurableProgramAnalysis cpa;
  private final LogManager logger;

  @Option(name="precisionReset", description="The value of marked unsafes, after which the precision should be cleaned",
      secure = true)
  private int precisionReset = Integer.MAX_VALUE;

  // TODO Option is broken!!
  @Option(
    name = "totalARGCleaning",
    description = "clean all ARG or try to reuse some parts of it (memory consuming)",
    secure = true
  )
  private boolean totalARGCleaning = true;

  @Option(
      name = "hideFilteredUnsafes",
      description = "filtered unsafes, which can not be removed using precision, may be hidden",
      secure = true)
  private boolean hideFilteredUnsafes = false;

  private final boolean disableAllCaching;

  private final Stats stats;
  private final BAMTransferRelation transfer;

  int i = 0;

  private final Map<AbstractIdentifier, AdjustablePrecision> precisionMap = new HashMap<>();

  public IdentifierIterator(
      ConfigurableRefinementBlock<AbstractIdentifier> pWrapper,
      Configuration config,
      ConfigurableProgramAnalysis pCpa,
      BAMTransferRelation pTransfer,
      boolean pDisableCaching)
      throws InvalidConfigurationException {
    super(pWrapper);
    config.inject(this);
    cpa = pCpa;
    UsageCPA uCpa = CPAs.retrieveCPA(pCpa, UsageCPA.class);
    logger = uCpa.getLogger();
    transfer = pTransfer;
    stats = new Stats();
    disableAllCaching = pDisableCaching;
  }

  public static Refiner create(ConfigurableProgramAnalysis pCpa) throws InvalidConfigurationException {
    if (!(pCpa instanceof WrapperCPA)) {
      throw new InvalidConfigurationException(BAMPredicateRefiner.class.getSimpleName() + " could not find the PredicateCPA");
    }

    UsageCPA usageCpa = ((WrapperCPA) pCpa).retrieveWrappedCpa(UsageCPA.class);
    if (usageCpa == null) {
      throw new InvalidConfigurationException(UsageCPA.class.getSimpleName() + " needs a UsageCPA");
    }

    return new RefinementBlockFactory(pCpa, usageCpa.getConfig())
        .create();
  }

  @Override
  public RefinementResult performBlockRefinement(ReachedSet pReached) throws CPAException, InterruptedException {

    UsageReachedSet uReached;

    if (pReached instanceof UsageReachedSet) {
      uReached = (UsageReachedSet) pReached;
    } else if (pReached instanceof ForwardingReachedSet) {
      uReached = (UsageReachedSet) ((ForwardingReachedSet) pReached).getDelegate();
    } else {
      throw new UnsupportedOperationException("UsageRefiner requires UsageReachedSet");
    }
    UsageContainer container = uReached.getUsageContainer();

    logger.log(Level.INFO, ("Perform US refinement: " + i++));
    boolean noNewPrecision = true;

    sendUpdateSignal(PredicateRefinerAdapter.class, pReached);
    sendUpdateSignal(PointIterator.class, container);

    Iterator<AbstractIdentifier> iterator = container.getUnrefinedUnsafeIterator();
    CFANode firstNode = AbstractStates.extractLocation(pReached.getFirstState());
    AdjustablePrecision initialPrecision =
        (AdjustablePrecision) cpa
            .getInitialPrecision(firstNode,
            StateSpacePartition.getDefaultPartition());

    while (iterator.hasNext()) {
      AbstractIdentifier currentId = iterator.next();

      stats.innerRefinementTimer.start();
      RefinementResult result = wrappedRefiner.performBlockRefinement(currentId);
      stats.innerRefinementTimer.stop();
      stats.iterations.inc();

      Collection<AdjustablePrecision> info = result.getPrecisions();

      if (result.isTrue()) {
        container.setAsRefined(currentId, result);
      } else if (hideFilteredUnsafes && result.isFalse() && info.isEmpty()) {
        // We do not add a precision, but consider the unsafe as false
        // set it as false now, because it will occur again, as precision is not changed
        // We can not look at precision size here - the result can be false due to heuristics
        container.setAsFalseUnsafe(currentId);
      } else if (!info.isEmpty()) {
        stats.precisionTimer.start();
        AdjustablePrecision oldPrecision =
            precisionMap.getOrDefault(currentId, initialPrecision);
        AdjustablePrecision updatedPrecision = oldPrecision;

        for (AdjustablePrecision p : info) {
          updatedPrecision = updatedPrecision.add(p);
        }

        assert !updatedPrecision.isEmpty() : "Updated precision is expected to be not empty";
        if (!updatedPrecision.equals(oldPrecision)) {
          precisionMap.put(currentId, updatedPrecision);
          // Need to restart the analysis to reproduce all unsafes
          // For example, we may remove some usage points
          noNewPrecision = false;
        }
        stats.precisionTimer.stop();

      }
    }

    if (noNewPrecision) {
      return RefinementResult.createFalse();
    }

    int counter = container.getProcessedUnsafeSize();
    if (counter >= precisionReset) {
      precisionMap.clear();
    }

    stats.finishingTimer.start();
    if (disableAllCaching) {
      BAMPredicateCPA bamcpa = CPAs.retrieveCPA(cpa, BAMPredicateCPA.class);
      if (bamcpa != null) {
        bamcpa.clearAllCaches();
      }
    }
    // ARGState.clearIdGenerator();
    // Note the following clean should be done always independently from option disableAllCaching
    if (totalARGCleaning && transfer != null) {
      transfer.cleanCaches();
    } else {
      /*
       * MultipleARGSubtreeRemover subtreesRemover = transfer.getMultipleARGSubtreeRemover();
       * subtreesRemover.cleanCaches();
       */
    }

    // Compose a new final precision, to avoid subtracting
    // And problems, if we remove precision, which is also present for other id
    AdjustablePrecision finalPrecision = initialPrecision;
    for (AdjustablePrecision val : precisionMap.values()) {
      finalPrecision = finalPrecision.add(val);
    }
    pReached.clear();

    // Get new state to remove all links to the old ARG
    pReached.add(
        cpa.getInitialState(firstNode, StateSpacePartition.getDefaultPartition()),
        finalPrecision);

    // TODO should we signal about removed ids?

    sendFinishSignal();
    stats.finishingTimer.stop();
    logger.log(Level.INFO, container.getUnsafeStatus());
    return RefinementResult.createTrue();
  }

  @Override
  public void printStatistics(StatisticsWriter pOut) {
    wrappedRefiner.printStatistics(pOut);
  }

  @Override
  public void collectStatistics(Collection<Statistics> statsCollection) {
    statsCollection.add(stats);
    super.collectStatistics(statsCollection);
  }

  @Override
  public boolean performRefinement(ReachedSet pReached) throws CPAException, InterruptedException {
    return performBlockRefinement(pReached).isTrue();
  }
}
