-- Containers (issue #90): an item may be a container holding other items.
--
-- The container itself gains only the capability flag. Containment is a location fact on the
-- *contained* item: the existing (location_kind, location_ref) pair absorbs the new case with no
-- DDL — a new CONTAINED kind value whose ref is the container's item id. As with GROUND and HELD,
-- the ref carries no foreign key: an item references where it is by identity, and whether that id
-- resolves (and resolves to an actual container) is an inter-aggregate rule, not the schema's.

alter table item
    add column container boolean not null default false;
