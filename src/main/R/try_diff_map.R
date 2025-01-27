library(tidyverse)
library(ggspatial)
library(sf)
library(ggmap)
library(RColorBrewer)
library(scales)
register_stadiamaps("1b5f7149-edba-47c0-bc09-4bdee5a889a2", write = FALSE)

diff_city <- read_csv("/Users/janek/repos/shared-svn/projects/mosaik-2/doc/40_publications/mosaik-2-02/data-files/scenarios/rp-city/city-diff-nox.xyt.csv")

diff_city_8am <- diff_city %>%
  filter(hour == 8) %>%
  st_as_sf(coords = c("x", "y"), crs = 25833) %>%
  st_transform(crs = 4326)

#%>%
mutate(
  lon = st_coordinates(.)[, 1],
  lat = st_coordinates(.)[, 2]
) %>%
  st_drop_geometry()

bbox <- make_bbox(lon, lat, data = diff_city_8am, f = 0.)
map <- get_stadiamap(bbox = bbox, maptype = "alidade_smooth", zoom = 14)
ggmap(map) +
  geom_point(data = diff_city_8am, aes(lon, lat, color = diff), shape = ".") +
  scale_color_viridis_c() +
  theme_light()

exposure_city <- read_csv("/Users/janek/repos/shared-svn/projects/mosaik-2/doc/40_publications/mosaik-2-02/data-files/scenarios/rp-city/exposure-map.csv") %>%
  st_as_sf(coords = c("x", "y"), crs = 25833) %>%
  st_transform(crs = 4326) %>%
  mutate(
    lon = st_coordinates(.)[, 1],
    lat = st_coordinates(.)[, 2]
  ) %>%
  st_drop_geometry()
exposure_berlin <- read_csv("/Users/janek/repos/shared-svn/projects/mosaik-2/doc/40_publications/mosaik-2-02/data-files/scenarios/rp-berlin/exposure-map.csv") %>%
  st_as_sf(coords = c("x", "y"), crs = 25833) %>%
  st_transform(crs = 4326) %>%
  mutate(
    lon = st_coordinates(.)[, 1],
    lat = st_coordinates(.)[, 2]
  ) %>%
  st_drop_geometry()

bbox <- make_bbox(lon, lat, data = exposure_city, f = 0.)
map <- get_stadiamap(bbox = bbox, maptype = "alidade_smooth", zoom = 14)

ggmap(map) +
  geom_point(data = exposure_city, aes(lon, lat, color = value * 1e6), shape = ".") +
  scale_color_viridis_c(option = "inferno", direction = -1, trans = "log") +
  ggtitle("Exposure for Toll in Innter-City") +
  theme_light()
ggmap(map) +
  geom_point(data = exposure_berlin, aes(lon, lat, color = value * 1e6), shape = ".") +
  scale_color_viridis_c(option = "inferno", direction = -1, trans = "log") +
  ggtitle("Exposure for Toll in Berlin") +
  theme_light()

exposure_base <- read_csv("/Users/janek/repos/shared-svn/projects/mosaik-2/doc/40_publications/mosaik-2-02/data-files/scenarios/base-case/exposure-map.csv") %>%
  st_as_sf(coords = c("x", "y"), crs = 25833) %>%
  st_transform(crs = 4326) %>%
  mutate(
    lon = st_coordinates(.)[, 1],
    lat = st_coordinates(.)[, 2]
  ) %>%
  st_drop_geometry()


diff_city_base <- exposure_base %>%
  left_join(exposure_city, join_by("time", "lon", "lat")) %>%
  mutate(diff = value.y - value.x) %>%
  select(lat, lon, diff)
diff_berlin_base <- exposure_base %>%
  left_join(exposure_berlin, join_by("time", "lon", "lat")) %>%
  mutate(diff = value.y - value.x) %>%
  select(lat, lon, diff)
diff_berlin_city <- exposure_berlin %>%
  left_join(exposure_city, join_by("time", "lon", "lat")) %>%
  mutate(diff = value.y - value.x) %>%
  select(lat, lon, diff)

# Define a signed square root transformation
signed_sqrt_trans <- trans_new(
  name = "signed_sqrt",
  transform = function(x) sign(x) * sqrt(abs(x)),  # Signed square root
  inverse = function(x) x^2 * sign(x)              # Inverse for the scale
)

# Define a signed log transformation
signed_log_trans <- trans_new(
  name = "signed_log",
  transform = function(x) sign(x) * log1p(abs(x)),  # Signed log (log1p handles small values well)
  inverse = function(x) sign(x) * (exp(abs(x)) - 1) # Inverse transformation
)

ggmap(map) +
  geom_point(data = diff_city_base, aes(lon, lat, color = diff * 1e6), shape = ".") +
  #scale_color_viridis_c(option = "inferno", direction = -1)+
  scale_color_gradient2(
    low = "#313695",     # Color for negative values
    mid = "white",    # Neutral color for zero or no difference
    high = "#a50026",     # Color for positive values
    midpoint = 0,     # Center the scale at zero
    trans = signed_log_trans    # Emphasize smaller values
  ) +
  ggtitle("Diff for Exposure city vs. base") +
  theme_light()
ggmap(map) +
  geom_point(data = diff_berlin_base, aes(lon, lat, color = diff * 1e6), shape = ".") +
  #scale_color_viridis_c(option = "inferno", direction = -1)+
  scale_color_gradient2(
    low = "#313695",     # Color for negative values
    mid = "white",    # Neutral color for zero or no difference
    high = "#a50026",     # Color for positive values
    midpoint = 0,     # Center the scale at zero
    trans = signed_log_trans    # Emphasize smaller values
  ) +
  ggtitle("Diff for Exposure Berlin vs. base") +
  theme_light()
ggmap(map) +
  geom_point(data = diff_berlin_city, aes(lon, lat, color = diff * 1e6), shape = ".") +
  #scale_color_viridis_c(option = "inferno", direction = -1)+
  scale_color_gradient2(
    low = "#313695",     # Color for negative values
    mid = "white",    # Neutral color for zero or no difference
    high = "#a50026",     # Color for positive values
    midpoint = 0,     # Center the scale at zero
    trans = signed_log_trans    # Emphasize smaller values
  ) +
  ggtitle("Diff for Exposure city vs. Berlin") +
  theme_light()
