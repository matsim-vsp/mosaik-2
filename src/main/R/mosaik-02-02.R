# add tidyverse and custom color palette which looks like google spread sheets
library(tidyverse)
cbPalette <- c("#4285f4", "#ea4335", "#fbbc04", "#34a853", "#ff6d01", "#46bdc6", "#7baaf7", "#f07b72", "#fcd04f", "#71c287")

palm_base <- read_csv("/Users/janek/Documents/writing/mosaik-2-02/data-files-nextcloud/scenarios/base-case/photoshade_6km10m_lod2_av_masked_M01.day2-si-units.xyt.csv") %>%
  mutate(hour = time / 3600)

palm_berlin <- read_csv("/Users/janek/Documents/writing/mosaik-2-02/data-files-nextcloud/scenarios/rp-berlin/photoshade_6km10m_berlin_av_masked_M01.day2-si-units.xyt.csv") %>%
  mutate(hour = time / 3600)

palm_city <- read_csv("/Users/janek/Documents/writing/mosaik-2-02/data-files-nextcloud/scenarios/rp-city/photoshade_6km10m_hundek_av_masked_M01.day2-si-units.xyt.csv") %>%
  mutate(hour = time / 3600)

base_nox <- palm_base %>% select(hour, x, y, NOx)
berlin_nox <- palm_berlin %>% select(hour, x, y, NOx)
city_nox <- palm_city %>% select(hour, x, y, NOx)

joined_nox <- base_nox %>%
  inner_join(berlin_nox, by = c("hour", "x", "y"), suffix = c("", ".berlin")) %>%
  inner_join(city_nox, by = c("hour", "x", "y"), suffix = c(".base", ".city")) %>%
  pivot_longer(cols = c(ends_with(".berlin"), ends_with(".base"), ends_with(".city")), values_to = "NOx")

#----- write diff csv
diffs_nox <- berlin_nox %>%
  inner_join(city_nox, by = c("hour", "x", "y"), suffix = c(".berlin", ".city")) %>%
  pivot_longer(cols = c(ends_with(".berlin"), ends_with(".city")), values_to = "NOx") %>%
  left_join(base_nox, by = c("hour", "x", "y"), suffix = c("", ".base")) %>%
  mutate(diff = NOx - NOx.base)

diff_nox_berlin <- diffs_nox %>%
  filter(name == "NOx.berlin") %>%
  select(hour, x, y, diff)
write_csv(diff_nox_berlin, "/Users/janek/Documents/writing/mosaik-2-02/data-files-nextcloud/scenarios/rp-berlin/berlin-diff-nox.xyt.csv")

diff_nox_city <- diffs_nox %>%
  filter(name == "NOx.city") %>%
  select(hour, x, y, diff)
write_csv(diff_nox_city, "/Users/janek/Documents/writing/mosaik-2-02/data-files-nextcloud/scenarios/rp-city/city-diff-nox.xyt.csv")
