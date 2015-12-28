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
package org.sosy_lab.cpachecker.util;

import org.sosy_lab.cpachecker.cfa.types.c.CCompositeType;
import org.sosy_lab.cpachecker.cfa.types.c.CType;


public class BnBRegionImpl implements BnBRegion{
  private final CCompositeType regionParent;
  private final CType elemType;
  private final String elemName;
  private final Integer number;

  /**
   * @param pType - types that will be present in this region
   * @param parent - struct with fields or null if global
   * @param name - field name
   * @param num - number of the field in parent
   */
  public BnBRegionImpl(CType pType, CCompositeType parent, String name, Integer num){
    regionParent = parent;
    elemType = pType;
    elemName = name;
    number = num;
  }

  @Override
  public CType getType(){
    return elemType;
  }

  @Override
  public CCompositeType getRegionParent(){
    return regionParent;
  }

  @Override
  public String getElem(){
    return elemName;
  }

  @Override
  public boolean isPartOfGlobal() {
    return regionParent == null;
  }

  @Override
  public Integer getFieldNumber(){
    return number;
  }
}
