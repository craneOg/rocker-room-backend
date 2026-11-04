package com.crane.bandwebbackend.global.serialization;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import java.io.IOException;
import java.util.Collection;

class IdStringSerializer extends StdSerializer<Object> {

    IdStringSerializer() {
        super(Object.class);
    }

    @Override
    public void serialize(Object value, JsonGenerator gen, SerializerProvider provider) throws IOException {
        if (value instanceof Collection<?> ids) {
            gen.writeStartArray();
            for (Object id : ids) {
                if (id == null) {
                    gen.writeNull();
                } else {
                    gen.writeString(id.toString());
                }
            }
            gen.writeEndArray();
        } else {
            gen.writeString(value.toString());
        }
    }
}
