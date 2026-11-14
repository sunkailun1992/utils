package com.kellen.utils.convert;

import lombok.Data;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 固定 {@link GeneralConvertor} 对外行为：形态照 user、message 的实际调用（BO/Query → 实体、实体 → VO），
 * 替换底层实现前后都必须通过。
 */
class GeneralConvertorTest {

    private static final LocalDateTime CREATED = LocalDateTime.of(2026, 11, 15, 8, 30, 15);

    @Test
    void convertsFlatFieldsLikeBoToEntity() {
        SourceBo source = source("alice");

        TargetEntity target = GeneralConvertor.convertor(source, TargetEntity.class);

        assertThat(target.getName()).isEqualTo("alice");
        assertThat(target.getState()).isEqualTo(State.ENABLED);
        assertThat(target.getType()).isEqualTo(2);
        assertThat(target.getStateDesc()).isNull();
    }

    @Test
    void copiesInheritedBaseFieldsIncludingIsDelete() {
        SourceBo deleted = source("bob");
        SourceBo unknown = source("carol");
        unknown.setIsDelete(null);

        TargetEntity fromDeleted = GeneralConvertor.convertor(deleted, TargetEntity.class);
        TargetEntity fromUnknown = GeneralConvertor.convertor(unknown, TargetEntity.class);

        assertThat(fromDeleted.getId()).isEqualTo("id-bob");
        assertThat(fromDeleted.getCreateDateTime()).isEqualTo(CREATED);
        assertThat(fromDeleted.getIsDelete()).isTrue();
        assertThat(fromDeleted.getVersion()).isEqualTo(3);
        assertThat(fromDeleted.getTenantId()).isEqualTo("tenant-a");
        assertThat(fromUnknown.getIsDelete()).isNull();
    }

    @Test
    void returnsNullForNullSource() {
        SourceBo none = null;
        List<SourceBo> noneList = null;

        assertThat(GeneralConvertor.convertor(none, TargetEntity.class)).isNull();
        assertThat(GeneralConvertor.convertor(noneList, TargetEntity.class)).isNull();
    }

    @Test
    void listOverloadKeepsOrderInArrayList() {
        List<SourceBo> sources = List.of(source("a"), source("b"), source("c"));

        List<TargetEntity> targets = GeneralConvertor.convertor(sources, TargetEntity.class);

        assertThat(targets).isInstanceOf(ArrayList.class);
        assertThat(targets).extracting(TargetEntity::getName).containsExactly("a", "b", "c");
    }

    @Test
    void deepCopiesNestedBeanAndCollection() {
        Nested nested = new Nested();
        nested.setLabel("inner");
        nested.setTags(new ArrayList<>(List.of("x", "y")));
        Holder source = new Holder();
        source.setNested(nested);
        source.setItems(new ArrayList<>(List.of("one", "two")));

        Holder copy = GeneralConvertor.convertor(source, Holder.class);

        assertNotSame(source.getNested(), copy.getNested());
        assertThat(copy.getNested().getLabel()).isEqualTo("inner");
        assertNotSame(source.getNested().getTags(), copy.getNested().getTags());
        assertThat(copy.getNested().getTags()).containsExactly("x", "y");
        assertNotSame(source.getItems(), copy.getItems());
        assertThat(copy.getItems()).containsExactly("one", "two");
    }

    @Test
    void copyIntoExistingTargetOverwritesWithSourceNulls() {
        SourceBo source = source("dave");
        source.setName(null);
        TargetEntity viaConvertor = prefilledTarget();
        TargetEntity viaCopy = prefilledTarget();

        GeneralConvertor.convertor(source, viaConvertor);
        GeneralConvertor.copyConvertor(source, viaCopy);

        for (TargetEntity target : List.of(viaConvertor, viaCopy)) {
            assertThat(target.getName()).isNull();
            assertThat(target.getState()).isEqualTo(State.ENABLED);
            assertThat(target.getStateDesc()).isEqualTo("kept");
        }
    }

    @Test
    void setOverloadRemoved() {
        assertThrows(NoSuchMethodException.class,
                () -> GeneralConvertor.class.getMethod("convertor", Set.class, Class.class));
    }

    private static SourceBo source(String name) {
        SourceBo source = new SourceBo();
        source.setId("id-" + name);
        source.setCreateDateTime(CREATED);
        source.setIsDelete(Boolean.TRUE);
        source.setVersion(3);
        source.setTenantId("tenant-a");
        source.setName(name);
        source.setState(State.ENABLED);
        source.setType(2);
        source.setTenantIds(List.of(1L, 2L));
        source.setCurrent(5L);
        return source;
    }

    private static TargetEntity prefilledTarget() {
        TargetEntity target = new TargetEntity();
        target.setName("old");
        target.setState(State.DISABLED);
        target.setStateDesc("kept");
        return target;
    }

    public enum State {
        ENABLED,
        DISABLED
    }

    @Data
    public static class Base {
        private String id;
        private LocalDateTime createDateTime;
        private Boolean isDelete;
        private Integer version;
        private String tenantId;
    }

    @Data
    @lombok.EqualsAndHashCode(callSuper = true)
    public static class SourceBo extends Base {
        private String name;
        private State state;
        private Integer type;
        private List<Long> tenantIds;
        private Long current;
    }

    @Data
    @lombok.EqualsAndHashCode(callSuper = true)
    public static class TargetEntity extends Base {
        private String name;
        private State state;
        private Integer type;
        private String stateDesc;
    }

    @Data
    public static class Nested {
        private String label;
        private List<String> tags;
    }

    @Data
    public static class Holder {
        private Nested nested;
        private List<String> items;
    }
}
