package core.framework.impl.validate.type;

import java.lang.reflect.Field;

/**
 * @author neo
 */
public interface TypeVisitor {
    void visitClass(Class<?> objectClass, String path);

    void visitField(Field field, String parentPath);
}
