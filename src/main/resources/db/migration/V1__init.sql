create table geocode_cache (
    id uuid primary key default gen_random_uuid(),
    query text not null unique,
    provider text not null,
    lat numeric(10, 7),
    lon numeric(10, 7),
    confidence numeric(4, 3) not null,
    raw_response jsonb,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table parser_run (
    id uuid primary key default gen_random_uuid(),
    source_url text not null,
    started_at timestamptz not null default now(),
    finished_at timestamptz,
    status text not null,
    raw_rows integer not null default 0,
    normalized_rows integer not null default 0,
    geocoded_rows integer not null default 0,
    event_count integer not null default 0,
    source_hash text,
    error_message text
);

create table hotspot (
    id uuid primary key default gen_random_uuid(),
    name text not null,
    category text not null,
    weight integer not null,
    geom geometry(Geometry, 4326) not null
);

create table district (
    id uuid primary key default gen_random_uuid(),
    name text not null unique,
    name_normalized text not null unique,
    geom geometry(MultiPolygon, 4326)
);

create index idx_geocode_cache_query on geocode_cache(query);
create index idx_hotspot_geom on hotspot using gist(geom);
create index idx_district_geom on district using gist(geom);
