// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2022 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.cpa.usage;

import java.util.List;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;

public interface WitnessNoteProvider {
  public String getNoteForEdge(CFAEdge pEdge);

  public default List<CFAEdge> filterIrrelevantEdges(List<CFAEdge> pEdges) {
    return pEdges;
  }
}
