-- World schema: scenes and their exits.
--
-- A scene is the aggregate root; its exits are owned children (no independent
-- lifecycle), so they live in a child table referenced back to their scene.
-- The schema is a deliberate design artifact (no ORM ddl-auto): ids are the
-- authored short keys carried by the domain SceneId value object (e.g. 'scn1').

create table scene
(
    id                varchar(64)  primary key,
    name              varchar(255) not null,
    short_description text         not null,
    full_description  text         not null
);

create table exit
(
    -- Ownership back-reference: which scene this exit belongs to. Mapped by
    -- Spring Data JDBC's @MappedCollection. FK-enforced — an exit cannot
    -- outlive its scene.
    scene_id        varchar(64)  not null references scene (id),

    -- Direction label, unique within the owning scene. The composite PK below
    -- enforces the domain's "no two exits share a name" invariant at the DB level.
    name            varchar(255) not null,

    -- The scene this exit leads to, referenced by identity. Deliberately NOT a
    -- foreign key: whether the target resolves to a known scene is an
    -- inter-aggregate world-consistency rule, validated by the world-construction
    -- use case so it yields a meaningful domain error rather than an FK violation
    -- (and so a two-pass seed can insert scenes whose targets are not yet present).
    target_scene_id varchar(64)  not null,

    primary key (scene_id, name)
);
