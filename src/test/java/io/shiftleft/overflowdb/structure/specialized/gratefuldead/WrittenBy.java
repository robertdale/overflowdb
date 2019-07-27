package io.shiftleft.overflowdb.structure.specialized.gratefuldead;

import io.shiftleft.overflowdb.structure.EdgeLayoutInformation;
import io.shiftleft.overflowdb.structure.NodeRef;
import io.shiftleft.overflowdb.structure.OverflowDbEdge;
import io.shiftleft.overflowdb.structure.OverflowDbNode;
import io.shiftleft.overflowdb.structure.OverflowElementFactory;
import io.shiftleft.overflowdb.structure.TinkerGraph;

import java.io.Serializable;
import java.util.Arrays;
import java.util.HashSet;

public class WrittenBy extends OverflowDbEdge implements Serializable {
  public static final String LABEL = "writtenBy";
  public static final HashSet<String> PROPERTY_KEYS = new HashSet<>(Arrays.asList());

  public WrittenBy(TinkerGraph graph, NodeRef<OverflowDbNode> outVertex, NodeRef<OverflowDbNode> inVertex) {
    super(graph, LABEL, outVertex, inVertex, PROPERTY_KEYS);
  }

  public static final EdgeLayoutInformation layoutInformation = new EdgeLayoutInformation(LABEL, PROPERTY_KEYS);

  public static OverflowElementFactory.ForEdge<WrittenBy> factory = new OverflowElementFactory.ForEdge<WrittenBy>() {
    @Override
    public String forLabel() {
      return WrittenBy.LABEL;
    }

    @Override
    public WrittenBy createEdge(TinkerGraph graph, NodeRef outVertex, NodeRef inVertex) {
      return new WrittenBy(graph, outVertex, inVertex);
    }
  };
}
