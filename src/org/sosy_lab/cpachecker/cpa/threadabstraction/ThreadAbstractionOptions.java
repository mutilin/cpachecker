// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2022 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.cpa.threadabstraction;

import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;

@Options(prefix = "cpa.threadabstraction")
public class ThreadAbstractionOptions {

  @Option(
    secure = true,
    name = "optimizePrecisionUpdate",
    description = "Which states is precision updated for?")
  private PrecisionUpdate precisionUpdate = PrecisionUpdate.PROJECTIONS;

  public static enum PrecisionUpdate {
    ALL, // update precision for all states (for debugging purposes)
    PROJECTIONS, // update precision for all states that projections are projected from
    COMMON_PREDECESSOR; // update precision for a common predecessor of all states that
                        // projections are projected from
  }

  @Option(
    secure = true,
    name = "addProjectionsToPrecision",
    description = "Which projections are added to precision?")
  private AddProjections addProjections = AddProjections.ALL;

  public static enum AddProjections {
    EVERYTHING_IN_PRECISION, // refiner is off, all projections are already in precision (for
                             // debugging purposes)
    ALL, // all projections in the path are added to precision
    REPEATED, // projections that are applied twice in the path are added to precision
    ORDER; // only projections that are in the wrong order are added to precision
  }

  public PrecisionUpdate optimizePrecisionUpdate() {
    return precisionUpdate;
  }

  public AddProjections addProjectionsToPrecision() {
    return addProjections;
  }
}
