/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2015  Dirk Beyer
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
package org.sosy_lab.cpachecker.cpa.smg.refiner;

import com.google.common.collect.Sets;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.core.counterexample.CounterexampleInfo;
import org.sosy_lab.cpachecker.core.interfaces.ConfigurableProgramAnalysis;
import org.sosy_lab.cpachecker.core.interfaces.Refiner;
import org.sosy_lab.cpachecker.core.reachedset.ReachedSet;
import org.sosy_lab.cpachecker.cpa.arg.ARGCPA;
import org.sosy_lab.cpachecker.cpa.arg.ARGState;
import org.sosy_lab.cpachecker.cpa.smg.SMGCPA;
import org.sosy_lab.cpachecker.exceptions.CPAException;
import org.sosy_lab.cpachecker.util.CPAs;

public class SMGBasicRefiner implements Refiner {

  private final LogManager logger;

  private final ARGCPA argCpa;
  private static Set<ARGState> cache;

  private SMGBasicRefiner(LogManager pLogger, ARGCPA pArgCpa) {
    logger = pLogger;
    argCpa = pArgCpa;
  }

  public static final SMGBasicRefiner create(ConfigurableProgramAnalysis pCpa)
      throws InvalidConfigurationException {

    ARGCPA argCpa = retrieveCPA(pCpa, ARGCPA.class);
    SMGCPA smgCpa = retrieveCPA(pCpa, SMGCPA.class);

    LogManager logger = smgCpa.getLogger();
    cache = Sets.newHashSet();

    return new SMGBasicRefiner(logger, argCpa);
  }

  @Override
  public boolean performRefinement(ReachedSet pReached) throws CPAException, InterruptedException {
    final Map<ARGState, CounterexampleInfo> counterexamples =
        argCpa.getARGExporter().getAllCounterexamples(pReached);

    logger.log(Level.FINEST, "Filtering new SMG counterexample.");
    for (Map.Entry<ARGState, CounterexampleInfo> cex : counterexamples.entrySet()) {
      final ARGState lastSate = cex.getKey();
      if (!cache.contains(lastSate)) {
        argCpa.getARGExporter().exportCounterexampleOnTheFly(lastSate, cex.getValue());
        cache.add(lastSate);
      }
    }
    logger.log(Level.FINEST, "SMG counterexample has been exported.");
    return false;
  }

  /** retrieve the wrapped CPA or throw an exception. */
  private static final <T extends ConfigurableProgramAnalysis> T retrieveCPA(
      ConfigurableProgramAnalysis pCpa, Class<T> retrieveCls) throws InvalidConfigurationException {
    final T extractedCPA = CPAs.retrieveCPA(pCpa, retrieveCls);
    if (extractedCPA == null) {
      throw new InvalidConfigurationException(
          retrieveCls.getSimpleName() + " cannot be retrieved.");
    }
    return extractedCPA;
  }
}
