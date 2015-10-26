package core.framework.impl.template.html;

/**
 * @author neo
 */
public enum HTMLTokenType {
    START_TAG,              // <tag
    START_TAG_END,          // >
    START_TAG_END_CLOSE,    // />
    END_TAG,        // </tag>
    TEXT,
    ATTR_NAME,      // attr
    ATTR_VALUE,     // =value or ="value"
    START_COMMENT,  // <!--
    END_COMMENT,    // comment-->
    EOF
}
