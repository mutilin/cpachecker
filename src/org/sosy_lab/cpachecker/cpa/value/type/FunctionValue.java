/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2017  Dirk Beyer
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
package org.sosy_lab.cpachecker.cpa.value.type;

import org.sosy_lab.cpachecker.cfa.types.c.CType;

import java.io.Serializable;

public class FunctionValue  implements Value, Serializable  {

  private static final long serialVersionUID = -3829943575180448170L;

  private String str;

  /**
   * Creates a new <code>FunctionValue</code>.
   * @param pString the value of the function
   */
  public FunctionValue(String pString) {
    str = pString;
  }

  public String getName()
  {
    return str;
  }

  @Override
  public boolean isNumericValue() {
    // TODO Auto-generated method stub
    return false;
  }

  @Override
  public boolean isUnknown() {
    // TODO Auto-generated method stub
    return false;
  }

  @Override
  public boolean isExplicitlyKnown() {
    // TODO Auto-generated method stub
    return false;
  }

  @Override
  public NumericValue asNumericValue() {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public Long asLong(CType pType) {
    // TODO Auto-generated method stub
    return null;
  }

  public String getString() {
    return str;
  }

  @Override
  public boolean equals(Object other) {
    if (other instanceof FunctionValue) {
      return this.getString().equals(((FunctionValue) other).getString());
    } else {
      return false;
    }
  }

}
