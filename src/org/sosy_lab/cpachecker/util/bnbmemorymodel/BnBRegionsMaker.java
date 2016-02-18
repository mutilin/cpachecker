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
  private static final int GLOBAL_IND = -1;
  public final String GLOBAL = " global";
  private Set<CType> containers = new HashSet<>();
  private Map<String, List<PointerTarget>> targetRegions = new HashMap<>();
  private Map<PointerTarget, Triple<String, String, Integer>> pointerTargets = new HashMap<>();

  /**
   *
   * @param parent - element's base
   * @param name - name of element
   * @return index or -1 if global
   * @throws Exception
   */
  public int getRegionIndexByParentAndName(CType parent, String name){

    BnBRegionImpl current;

    System.out.println("CALL GRI");
    System.out.println(parent);
    System.out.println(name);

    for (int i = 0; i < regions.size(); ++i){
      current = regions.get(i);
      System.out.println(current);
      if (current.getElem().equals(name)
          && current.getRegionParent().equals(parent)){
        System.out.println("Found\n");
        if (current.isPartOfGlobal()){
          return GLOBAL_IND;
        } else {
          return i;
        }

      }
    }
    (new Exception("Not found " + parent + " " + name)).printStackTrace();
    return GLOBAL_IND;
  }

  public void makeRegions(CFA cfa) {
    ComplexTypeFieldStatistics ctfs = new ComplexTypeFieldStatistics();
    ctfs.findFieldsInCFA(cfa);
    ctfs.dumpStat("Stat.txt");

    Map<CType, HashMap<CType, HashSet<Pair<String, Integer>>>> usedFields = ctfs.getUsedFields();
    Map<CType, HashMap<CType, HashSet<Pair<String, Integer>>>> refdFields = ctfs.getRefdFields();
    Map<CType, HashSet<Pair<String, Integer>>> sub;

    // remove all those fields present in both maps
    for (CType basicType : usedFields.keySet()){
      if (refdFields.containsKey(basicType)){
        sub = usedFields.get(basicType);
        for (CType structType : sub.keySet()){
          HashSet<Pair<String, Integer>> set = refdFields.get(basicType).get(structType);

          if (set != null){
            usedFields.get(basicType).get(structType).removeAll(set);
          }

        }
      }
    }

    // fill regions
    for (CType basicType : refdFields.keySet()){
      for (CType structType : refdFields.get(basicType).keySet()){
        for (Pair<String, Integer> name : refdFields.get(basicType).get(structType)){

          regions.add(new BnBRegionImpl(basicType, null, structType.toString() + "::" + name.getFirst(), name.getSecond()));

        }
      }
    }

    for (CType basicType : usedFields.keySet()){
      for (CType structType : usedFields.get(basicType).keySet()){

        Set<Pair<String, Integer>> set = usedFields.get(basicType).get(structType);
        if (!set.isEmpty()) {
          for (Pair<String, Integer> name : set){
            regions.add(new BnBRegionImpl(basicType, (CCompositeType) structType, name.getFirst(), name.getSecond()));
          }
        }
      }
    }

    for (BnBRegionImpl region : regions){
      containers.add(region.getRegionParent());
    }

  }

  public void dumpRegions(String filename){
    File dump = new File(filename);

    try{
      FileWriter writer = new FileWriter(dump);

      String result = "";

      for (CType container : containers){
        result += ((CCompositeType)container).getMembers();
        result += '\n';
      }

      int i = 0;
      for (BnBRegionImpl reg : regions){
        result += "Number: " + (i++) + '\n';
        result += "Type: " + reg.getType().toString() + '\n';
        result += "Parent: ";

        if (reg.getRegionParent() == null){
          result += "NULL";
        } else {
          result += reg.getRegionParent().toString();
        }
        result += '\n';
        result += "Member:\n";
        result += '\t' + reg.getElem() + "\n\n";

      }

      writer.write(result);
      writer.close();

    } catch (IOException e){
      System.out.println(e.getMessage());
    }
  }

  public Map<String, PersistentList<PointerTarget>> getNewTargetsWithRegions(
      PersistentSortedMap<String, PersistentList<PointerTarget>> targets,
      PointerTargetSetBuilder ptsb){

    for (String target : targets.keySet()){
      for (PointerTarget pt : targets.get(target)){
        CType containerType = pt.getContainerType();
        if (containers.contains(containerType)
            && containerType instanceof CCompositeType){
          int offset = pt.getContainerOffset();
          int curOffset = 0;

          for (CCompositeTypeMemberDeclaration field : ((CCompositeType) containerType).getMembers()){
            if (curOffset == offset){
              pointerTargets.put(pt, Triple.of(target, containerType.toString() + " " + field.getName(),
                      getRegionIndexByParentAndName(containerType, field.getName())));
            } else {
              offset += ptsb.getSize(field.getType());
            }
          }
        } else {
          pointerTargets.put(pt, Triple.of(target, "global", -1));
        }
      }
    }

    for (PointerTarget pt : pointerTargets.keySet()){
      Triple<String, String, Integer> triple = pointerTargets.get(pt);
      String key;
      String first = triple.getFirst();
      if (first.contains("global") || first.contains("struct")){
        key = first;
      } else {
        key = first + " " + triple.getSecond();
      }
      if (!targetRegions.containsKey(key)){
        targetRegions.put(key, new ArrayList<PointerTarget>());
      }
      if (!targetRegions.get(key).contains(pt)){
        targetRegions.get(key).add(pt);
      }
    }

    for (PointerTarget pt : pointerTargets.keySet()){
      System.out.println("UTR: " + pt.getBase() + ' ' +
          pt.getOffset() + ' ' + targetRegions.get(pt));
    }

    System.out.println(targetRegions);
    System.out.println(targets);
    System.out.println("UTR: ###########");

    Map<String, PersistentList<PointerTarget>> newTargets = new HashMap<>();
    for (String type : targetRegions.keySet()){
      PersistentList<PointerTarget> pll =
          PersistentLinkedList.copyOf(targetRegions.get(type));
      newTargets.put(type, pll);
    }

    for (String type : targets.keySet()){
      if (! (type.contains("global") || type.contains("struct"))){
        List<PointerTarget> toAdd = new ArrayList<>();
        for (PointerTarget pt : targets.get(type)){
          if (getRegionIndex(pt) < 0){
            toAdd.add(pt);
          }
        }
        newTargets.put(type + " global", PersistentLinkedList.copyOf(toAdd));
      } else {
        newTargets.put(type, targets.get(type));
      }
    }

    return newTargets;
  }

  public int getRegionIndex(PointerTarget pt){
    if (pointerTargets.keySet().contains(pt)){
      return pointerTargets.get(pt).getThird();
    } else {
      return GLOBAL_IND;
    }
  }

  public PointerTargetSet updatePTS(PointerTargetSet pts) {
    Map<String, PersistentList<PointerTarget>> result = new HashMap<>();
    for (String key: targetRegions.keySet()){
      PersistentList<PointerTarget> pl = PersistentLinkedList.copyOf(targetRegions.get(key));
      result.put(key, pl);
    }
    return new PointerTargetSet(pts, PathCopyingPersistentTreeMap.copyOf(result));
  }

  public String getNewUfName(String ufName, String region){
    System.out.println(region == null);
    ufName += "_";
    if (region != null){
      ufName += region.replace(' ', '_');
    } else {
      ufName += "global";
      (new Exception("Global region")).printStackTrace();
    }
    return ufName;
  }

}
