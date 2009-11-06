/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker. 
 *
 *  Copyright (C) 2007-2008  Dirk Beyer and Erkan Keremoglu.
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
 *    http://www.cs.sfu.ca/~dbeyer/CPAchecker/
 */
package cpa.invariant.controller;

import cfa.objectmodel.CFAEdge;

/**
 * Data that needs to be tracked by a stopping heuristics
 * @author g.theoduloz
 */
public interface StopHeuristicsData {
  public boolean isBottom();
  public boolean isTop();
  public boolean isLessThan(StopHeuristicsData d);
  
  /** Collect data with respect to the given set of reached states */
  public StopHeuristicsData collectData(Iterable<StopHeuristicsData> reached);
  
  /** Process an edge and update the data */
  public StopHeuristicsData processEdge(CFAEdge edge);
}
