library(tidyverse)
library(dplyr)
library(ggspatial)
library(sf)
library(ggmap)
register_stadiamaps("1b5f7149-edba-47c0-bc09-4bdee5a889a2", write = FALSE)

base_raster <- read_csv("/Users/janek/Documents/writing/mosaik-2-05/data-files/scenarios/base-case/photoshade_6km10m_lod2_av_masked_M01.NO2_day2-si-units.xyt.csv")
berlin_raster <- read_csv("/Users/janek/Documents/writing/mosaik-2-05/data-files/scenarios/rp-berlin/photoshade_6km10m_berlin_av_masked_M01.NO2_day2-si-units.xyt.csv")

diff_berlin <- base_raster %>%
  inner_join(berlin_raster, by = join_by(time, x, y), suffix = c(".base", ".compare")) %>%
  mutate(diff = value.compare - value.base) %>%
  mutate(diff_mug = diff * 1e6) %>%
  mutate(hour = time / 3600) %>%
  select(hour, x, y, diff, diff_mug)

write_csv(diff_berlin, "/Users/janek/Documents/writing/mosaik-2-05/data-files/scenarios/rp-berlin/berlin-diff-no2.xyt.csv")

diff_berlin_8am <- diff_berlin %>%
  filter(hour == 8) %>%
  filter(-200 < diff_mug & diff_mug < 200) %>%
  st_as_sf(coords = c("x", "y"), crs = 25833) %>%
  st_transform(crs = 4326) %>%
  mutate(
    lon = st_coordinates(.)[, 1],
    lat = st_coordinates(.)[, 2]
  ) %>%
  st_drop_geometry()
bbox <- make_bbox(lon, lat, data = diff_berlin_8am, f = 0.)
map <- get_stadiamap(bbox = bbox, maptype = "alidade_smooth", zoom = 14)
ggmap(map) +
  geom_point(data = diff_berlin_8am, aes(lon, lat, color = diff_mug), shape = ".") +
  scale_color_viridis_c() +
  ggtitle("Diff. NO2 Toll Berlin City")
theme_light()

city_raster <- read_csv("/Users/janek/Documents/writing/mosaik-2-05/data-files/scenarios/rp-city/photoshade_6km10m_hundek_av_masked_M01.NO2_day2-si-units.xyt.csv")

diff_city <- base_raster %>%
  inner_join(city_raster, by = join_by(time, x, y), suffix = c(".base", ".compare")) %>%
  mutate(diff = value.compare - value.base) %>%
  mutate(diff_mug = diff * 1e6) %>%
  mutate(hour = time / 3600) %>%
  select(hour, x, y, diff, diff_mug)

write_csv(diff_city, "/Users/janek/Documents/writing/mosaik-2-05/data-files/scenarios/rp-city/city-diff-no2.xyt.csv")

diff_city_8am <- diff_city %>%
  filter(hour == 8) %>%
  filter(-200 < diff_mug & diff_mug < 200) %>%
  st_as_sf(coords = c("x", "y"), crs = 25833) %>%
  st_transform(crs = 4326) %>%
  mutate(
    lon = st_coordinates(.)[, 1],
    lat = st_coordinates(.)[, 2]
  ) %>%
  st_drop_geometry()
ggmap(map) +
  geom_point(data = diff_city_8am, aes(lon, lat, color = diff_mug), shape = ".") +
  scale_color_viridis_c() +
  ggtitle("Diff. NO2 Toll Berlin City Center")
theme_light()