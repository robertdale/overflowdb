package io.shiftleft.overflowdb.tp3;

import io.shiftleft.overflowdb.OdbGraph;
import org.apache.commons.lang3.NotImplementedException;
import org.apache.tinkerpop.gremlin.structure.Edge;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.apache.tinkerpop.gremlin.structure.io.AbstractIoRegistry;
import org.apache.tinkerpop.gremlin.structure.io.IoRegistry;
import org.apache.tinkerpop.gremlin.structure.io.graphson.GraphSONIo;
import org.apache.tinkerpop.gremlin.structure.io.graphson.GraphSONTokens;
import org.apache.tinkerpop.gremlin.structure.io.graphson.TinkerPopJacksonModule;
import org.apache.tinkerpop.gremlin.structure.io.gryo.GryoIo;
import org.apache.tinkerpop.gremlin.structure.io.gryo.GryoWriter;
import org.apache.tinkerpop.shaded.jackson.core.JsonGenerator;
import org.apache.tinkerpop.shaded.jackson.core.JsonParser;
import org.apache.tinkerpop.shaded.jackson.core.JsonProcessingException;
import org.apache.tinkerpop.shaded.jackson.databind.DeserializationContext;
import org.apache.tinkerpop.shaded.jackson.databind.SerializerProvider;
import org.apache.tinkerpop.shaded.jackson.databind.deser.std.StdDeserializer;
import org.apache.tinkerpop.shaded.jackson.databind.ser.std.StdScalarSerializer;
import org.apache.tinkerpop.shaded.kryo.Kryo;
import org.apache.tinkerpop.shaded.kryo.Serializer;
import org.apache.tinkerpop.shaded.kryo.io.Input;
import org.apache.tinkerpop.shaded.kryo.io.Output;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * An implementation of the {@link IoRegistry} interface that provides serializers with custom configurations for
 * implementation specific classes that might need to be serialized.  This registry allows a {@link OdbGraph} to
 * be serialized directly which is useful for moving small graphs around on the network.
 * <p/>
 * Most providers need not implement this kind of custom serializer as they will deal with much larger graphs that
 * wouldn't be practical to serialize in this fashion.  This is a bit of a special case for OverflowDb given its
 * in-memory status.  Typical implementations would create serializers for a complex vertex identifier or a
 * custom data class like a "geographic point".
 *
 * @author Stephen Mallette (http://stephen.genoprime.com)
 */
public final class TinkerIoRegistryV2d0 extends AbstractIoRegistry {

  private static final TinkerIoRegistryV2d0 INSTANCE = new TinkerIoRegistryV2d0();

  private TinkerIoRegistryV2d0() {
    register(GryoIo.class, OdbGraph.class, new GryoSerializer());
    register(GraphSONIo.class, null, new TinkerModuleV2d0());
  }

  public static TinkerIoRegistryV2d0 instance() {
    return INSTANCE;
  }

  /**
   * Provides a method to serialize an entire {@link OdbGraph} into itself for Gryo.  This is useful when
   * shipping small graphs around through Gremlin Server. Reuses the existing Kryo instance for serialization.
   */
  final static class GryoSerializer extends Serializer<OdbGraph> {
    @Override
    public void write(final Kryo kryo, final Output output, final OdbGraph graph) {
      try (final ByteArrayOutputStream stream = new ByteArrayOutputStream()) {
        GryoWriter.build().mapper(() -> kryo).create().writeGraph(stream, graph);
        final byte[] bytes = stream.toByteArray();
        output.writeInt(bytes.length);
        output.write(bytes);
      } catch (Exception io) {
        throw new RuntimeException(io);
      }
    }

    @Override
    public OdbGraph read(final Kryo kryo, final Input input, final Class<OdbGraph> clazz) {
      throw new NotImplementedException("");
//      final Configuration conf = new BaseConfiguration();
//      conf.setProperty("gremlin.tinkergraph.defaultVertexPropertyCardinality", "list");
//      final OdbGraph graph = OdbGraph.open(conf);
//      final int len = input.readInt();
//      final byte[] bytes = input.readBytes(len);
//      try (final ByteArrayInputStream stream = new ByteArrayInputStream(bytes)) {
//        GryoReader.build().mapper(() -> kryo).create().readGraph(stream, graph);
//      } catch (Exception io) {
//        throw new RuntimeException(io);
//      }
//
//      return graph;
    }
  }

  /**
   * Provides a method to serialize an entire {@link OdbGraph} into itself for GraphSON. This is useful when
   * shipping small graphs around through Gremlin Server.
   */
  final static class TinkerModuleV2d0 extends TinkerPopJacksonModule {
    public TinkerModuleV2d0() {
      super("tinkergraph-2.0");
      addSerializer(OdbGraph.class, new JacksonSerializer());
      addDeserializer(OdbGraph.class, new JacksonDeserializer());
    }

    @Override
    public Map<Class, String> getTypeDefinitions() {
      return new HashMap<Class, String>() {{
        put(OdbGraph.class, "graph");
      }};
    }

    @Override
    public String getTypeNamespace() {
      return "tinker";
    }
  }

  /**
   * Serializes the graph into an edge list format.  Edge list is a better choices than adjacency list (which is
   * typically standard from the {@link org.apache.tinkerpop.gremlin.structure.io.GraphReader} and {@link org.apache.tinkerpop.gremlin.structure.io.GraphWriter} perspective) in this case because
   * the use case for this isn't around massive graphs.  The use case is for "small" subgraphs that are being
   * shipped over the wire from Gremlin Server. Edge list format is a bit easier for non-JVM languages to work
   * with as a format and doesn't require a cache for loading (as vertex labels are not serialized in adjacency
   * list).
   */
  final static class JacksonSerializer extends StdScalarSerializer<OdbGraph> {

    public JacksonSerializer() {
      super(OdbGraph.class);
    }

    @Override
    public void serialize(final OdbGraph graph, final JsonGenerator jsonGenerator, final SerializerProvider serializerProvider)
        throws IOException {
      jsonGenerator.writeStartObject();
      jsonGenerator.writeFieldName(GraphSONTokens.VERTICES);
      jsonGenerator.writeStartArray();

      final Iterator<Vertex> vertices = graph.vertices();
      while (vertices.hasNext()) {
        serializerProvider.defaultSerializeValue(vertices.next(), jsonGenerator);
      }

      jsonGenerator.writeEndArray();
      jsonGenerator.writeFieldName(GraphSONTokens.EDGES);
      jsonGenerator.writeStartArray();

      final Iterator<Edge> edges = graph.edges();
      while (edges.hasNext()) {
        serializerProvider.defaultSerializeValue(edges.next(), jsonGenerator);
      }

      jsonGenerator.writeEndArray();
      jsonGenerator.writeEndObject();
    }
  }

  /**
   * Deserializes the edge list format.
   */
  static class JacksonDeserializer extends StdDeserializer<OdbGraph> {
    public JacksonDeserializer() {
      super(OdbGraph.class);
    }

    @Override
    public OdbGraph deserialize(final JsonParser jsonParser, final DeserializationContext deserializationContext) throws IOException, JsonProcessingException {
      throw new NotImplementedException("");
//      final Configuration conf = new BaseConfiguration();
//      conf.setProperty("gremlin.tinkergraph.defaultVertexPropertyCardinality", "list");
//      final OdbGraph graph = OdbGraph.open(conf);
//
//      while (jsonParser.nextToken() != JsonToken.END_OBJECT) {
//        if (jsonParser.getCurrentName().equals("vertices")) {
//          while (jsonParser.nextToken() != JsonToken.END_ARRAY) {
//            if (jsonParser.currentToken() == JsonToken.START_OBJECT) {
//              final DetachedVertex v = (DetachedVertex) deserializationContext.readValue(jsonParser, Vertex.class);
//              v.attach(Attachable.Method.getOrCreate(graph));
//            }
//          }
//        } else if (jsonParser.getCurrentName().equals("edges")) {
//          while (jsonParser.nextToken() != JsonToken.END_ARRAY) {
//            if (jsonParser.currentToken() == JsonToken.START_OBJECT) {
//              final DetachedEdge e = (DetachedEdge) deserializationContext.readValue(jsonParser, Edge.class);
//              e.attach(Attachable.Method.getOrCreate(graph));
//            }
//          }
//        }
//      }
//
//      return graph;
    }

    @Override
    public boolean isCachable() {
      return true;
    }
  }
}
