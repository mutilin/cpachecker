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
package org.sosy_lab.cpachecker.util.bnbmemorymodel;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.sosy_lab.common.Pair;
import org.sosy_lab.common.Triple;
import org.sosy_lab.common.collect.PathCopyingPersistentTreeMap;
import org.sosy_lab.common.collect.PersistentLinkedList;
import org.sosy_lab.common.collect.PersistentList;
import org.sosy_lab.common.collect.PersistentSortedMap;
import org.sosy_lab.cpachecker.cfa.CFA;
import org.sosy_lab.cpachecker.cfa.types.c.CCompositeType;
import org.sosy_lab.cpachecker.cfa.types.c.CCompositeType.CCompositeTypeMemberDeclaration;
import org.sosy_lab.cpachecker.cfa.types.c.CType;
import org.sosy_lab.cpachecker.util.predicates.pathformula.pointeraliasing.PointerTarget;
import org.sosy_lab.cpachecker.util.predicates.pathformula.pointeraliasing.PointerTargetSet;
import org.sosy_lab.cpachecker.util.predicates.pathformula.pointeraliasing.PointerTargetSetBuilder;


public class BnBRegionsMaker {

  private List<BnBRegionImpl> regions = new ArrayList<>();
  private Set<CType> containers = new HashSet<>();
  private Map<String, List<PointerTarget>> targetRegions = new HashMap<>();
  private Map<PointerTarget, Pair<String, String>> pointerTargets = new HashMap<>();

  /**
   *
   * @param parent - element's base
   * @param name - name of element
   * @return true if global, else otherwise
   */
  public boolean isInGlobalRegion(final CType parent, final String name){

    if (regions.isEmpty()){
      return true;
    }

    for (BnBRegion current : regions){
      if (current.getElem().equals(name)
          && current.getRegionParent().equals(parent)){
        if (current.isPartOfGlobal()){
          return true;
        } else {
          return false;
        }

      }
    }
   // (new Exception("Not found " + parent + " " + name)).printStackTrace();
    return true;
  }

  public void makeRegions(final CFA cfa) {
    ComplexTypeFieldStatistics ctfs = new ComplexTypeFieldStatistics();
    ctfs.findFieldsInCFA(cfa);
    //ctfs.dumpStat("Stat.txt");

    Map<CType, HashMap<CType, HashSet<String>>> usedFields = ctfs.getUsedFields();
    Map<CType, HashMap<CType, HashSet<String>>> refdFields = ctfs.getRefdFields();
    Map<CType, HashSet<String>> sub;

    // remove all those fields present in both maps
    for (CType basicType : usedFields.keySet()){
      if (refdFields.containsKey(basicType)){
        sub = usedFields.get(basicType);
        for (CType structType : sub.keySet()){
          Set<String> set = refdFields.get(basicType).get(structType);

          if (set != null){
            usedFields.get(basicType).get(structType).removeAll(set);
          }

        }
      }
    }

    // fill regions
    for (CType basicType : usedFields.keySet()){
      for (CType structType : usedFields.get(basicType).keySet()){

        Set<String> set = usedFields.get(basicType).get(structType);
        if (!set.isEmpty()) {
          for (String name : set){
            regions.add(new BnBRegionImpl(basicType, (CCompositeType) structType, name));
          }
        }
      }
    }

    for (BnBRegionImpl region : regions){
      containers.add(region.getRegionParent());
    }

  }

  public void dumpRegions(final String filename){
    File dump = new File(filename);

    try{
      FileWriter writer = new FileWriter(dump);

      String result = "";

      if (!containers.isEmpty()){
        for (CType container : containers){
          result += ((CCompositeType)container).getMembers();
          result += '\n';
        }
      } else {
        result += "Empty containers\n";
      }

      if (!regions.isEmpty()) {
        int i = 0;
        for (BnBRegionImpl reg : regions) {
          result += "Number: " + (i++) + '\n';
          result += "Type: " + reg.getType().toString() + '\n';
          result += "Parent: ";

          if (reg.getRegionParent() == null) {
            result += "NULL";
          } else {
            result += reg.getRegionParent().toString();
          }
          result += '\n';
          result += "Member:\n";
          result += '\t' + reg.getElem() + "\n\n";

        }
      } else {
        result += "Empty regions\n";
      }

      writer.write(result);
      writer.close();

    } catch (IOException e){
      System.out.println(e.getMessage());
    }
  }

  public Map<String, PersistentList<PointerTarget>> getNewTargetsWithRegions(
      final PersistentSortedMap<String, PersistentList<PointerTarget>> targets,
      final PointerTargetSetBuilder ptsb){

    //TODO: optimize function so it wouldn't build everything from the start each time we call it

    for (String target : targets.keySet()){
      for (PointerTarget pt : targets.get(target)){
        CType containerType = pt.getContainerType();
        if (containers.contains(containerType)
            && containerType instanceof CCompositeType){
          int offset = pt.getContainerOffset();
          int curOffset = 0;

          for (CCompositeTypeMemberDeclaration field : ((CCompositeType) containerType).getMembers()){
            if (curOffset == offset){
              pointerTargets.put(pt, Pair.of(target, " " + containerType.toString() + " " + field.getName()));
            } else {
              offset += ptsb.getSize(field.getType());
            }
          }
        } else {
          pointerTargets.put(pt, Pair.of(target, " global"));
        }
      }
    }

    for (PointerTarget pt : pointerTargets.keySet()){
      //TODO: there is a significant difference in performance
      //if we use pair of strings instead of one big string

      Pair<String, String> pair = pointerTargets.get(pt);
      String key = pair.getFirst();

      if (! (key.contains("global") || key.contains("struct"))){
        key += pair.getSecond();
      }

      if (!targetRegions.containsKey(key)){
        targetRegions.put(key, new ArrayList<PointerTarget>());
      }
      if (!targetRegions.get(key).contains(pt)){
        targetRegions.get(key).add(pt);
      }
    }

    Map<String, PersistentList<PointerTarget>> newTargets = new HashMap<>();
    for (String type : targetRegions.keySet()){
      newTargets.put(type, PersistentLinkedList.copyOf(targetRegions.get(type)));
    }

    for (String type : targets.keySet()){
      if (! (type.contains("global") || type.contains("struct"))){
        List<PointerTarget> toAdd = new ArrayList<>();
        for (PointerTarget pt : targets.get(type)){
          if (isInGlobalRegion(pt)){
            toAdd.add(pt);
          }
        }
        newTargets.put(type + " global", PersistentLinkedList.copyOf(toAdd));
      } else {
        newTargets.put(type, targets.get(type));
      }
    }

    targetRegions.clear();
    pointerTargets.clear();

    return newTargets;
  }

  public boolean isInGlobalRegion(final PointerTarget pt){
    return !pointerTargets.containsKey(pt);
  }

  public PointerTargetSet updatePTS(final PointerTargetSet pts) {
    Map<String, PersistentList<PointerTarget>> result = new HashMap<>();
    for (String key: targetRegions.keySet()){
      result.put(key, PersistentLinkedList.copyOf(targetRegions.get(key)));
    }
    return new PointerTargetSet(pts, PathCopyingPersistentTreeMap.copyOf(result));
  }

  public String getNewUfName(final String ufName, String region){
    String result = ufName + "_";
    if (region != null){
      result += region.replace(' ', '_');
    } else {
      result += "global";
      //(new Exception("Global region")).printStackTrace();
    }
    return result;
  }

}
