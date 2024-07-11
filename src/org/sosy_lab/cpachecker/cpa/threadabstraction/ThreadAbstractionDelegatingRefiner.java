// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2021 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.cpa.threadabstraction;

import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.ConfigurationBuilder;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.cpachecker.core.interfaces.ConfigurableProgramAnalysis;
import org.sosy_lab.cpachecker.core.interfaces.Refiner;
import org.sosy_lab.cpachecker.cpa.arg.AbstractARGBasedRefiner;
import org.sosy_lab.cpachecker.cpa.predicate.PredicateCPA;
import org.sosy_lab.cpachecker.cpa.predicate.PredicateRefiner;
import org.sosy_lab.cpachecker.util.CPAs;
import org.sosy_lab.cpachecker.util.refinement.DelegatingARGBasedRefiner;

public abstract class ThreadAbstractionDelegatingRefiner implements Refiner {

  @SuppressWarnings("resource")
  public static Refiner create(ConfigurableProgramAnalysis cpa)
      throws InvalidConfigurationException {

    PredicateCPA predicateCpa =
        CPAs.retrieveCPAOrFail(cpa, PredicateCPA.class, ThreadAbstractionRefiner.class);

    ConfigurationBuilder configBuilder = Configuration.builder();
    configBuilder = configBuilder.copyFrom(predicateCpa.getConfiguration());
    Configuration newConfig = configBuilder.build();

    return AbstractARGBasedRefiner.forARGBasedRefiner(
        new DelegatingARGBasedRefiner(
            predicateCpa.getLogger(),
            ThreadAbstractionRefinerWrapper.create0(newConfig),
            PredicateRefiner.create0(cpa)),
        cpa);
  }
}
