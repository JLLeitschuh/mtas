package mtas.codec.tree;

import java.util.ArrayList;

import mtas.codec.util.CodecSearchTree.MtasTreeHit;

public class IntervalTreeNodeData<T> {
  public int start;
  public int end;
  public int hitStart;
  public int hitEnd;
  public ArrayList<MtasTreeHit<T>> list;

  public IntervalTreeNodeData(int start, int end, int hitStart, int hitEnd) {
    this.start = start;
    this.end = end;
    this.hitStart = hitStart;
    this.hitEnd = hitEnd;
    list = new ArrayList<MtasTreeHit<T>>();
  }

  public String toString() {
    String text = this.getClass().getName();
    text += "(s" + start + "|hs" + hitStart + ",hh" + hitEnd + "|e" + end
        + ") - " + list;
    return text;
  }
}
