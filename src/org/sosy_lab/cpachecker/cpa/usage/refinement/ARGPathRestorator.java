/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2019  Dirk Beyer
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
 */
package org.sosy_lab.cpachecker.cpa.usage.refinement;

import java.util.List;
import java.util.Set;
import org.sosy_lab.cpachecker.cpa.arg.ARGState;
import org.sosy_lab.cpachecker.cpa.arg.ARGUtils;
import org.sosy_lab.cpachecker.cpa.arg.path.ARGPath;

public class ARGPathRestorator implements PathRestorator {

  @Override
  public ARGPath computePath(ARGState pLastElement) {
    return ARGUtils.getOnePathFromTo(s -> s.getParents().isEmpty(), pLastElement);
  }

  @Override
  public ARGPath computePath(ARGState pLastElement, Set<List<Integer>> pRefinedStates) {
    //Temporary implementation
    return computePath(pLastElement);
  }

  @Override
  public PathIterator iterator(ARGState pTarget) {
    throw new UnsupportedOperationException("Not implemented yet");
  }

}