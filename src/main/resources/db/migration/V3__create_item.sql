-- Item schema: things found on the ground throughout the world.
--
-- An item is an aggregate root with its own runtime-generated id (the domain
-- ItemId value object, prefix 'itm' + a NanoID body). Unlike scenes and the
-- player, item ids are not authored — one authored template spawns several
-- instances, each minted a fresh id. The schema is a deliberate design artifact
-- (no ORM ddl-auto).

create table item
(
    id                varchar(64)  primary key,
    short_description varchar(255) not null,
    full_description  text         not null,

    -- The scene this item currently lies in, referenced by identity. Deliberately
    -- NOT a foreign key (mirroring exit.target_scene_id and player.current_scene_id):
    -- an item references where it is, and whether that id resolves to a known scene
    -- is an inter-aggregate concern, not a database constraint.
    scene_id          varchar(64)  not null
);
