# add tidyverse and custom color palette which looks like google spread sheets
library(tidyverse)
library(patchwork)

cbPalette <- c("#4285f4", "#ea4335", "#fbbc04", "#34a853", "#ff6d01", "#46bdc6", "#7baaf7", "#f07b72", "#fcd04f", "#71c287")
scenario_color_palette <- c(
  "Base case" = "#4285f4",
  "Scenario 1" = "#34a853",
  "Scenario 2" = "#ea4335",
  "Scenario 3" = "#fbbc04"
)

get_name_1 <- function(value) {
  if (grepl(".base", value, fixed = TRUE)) {
    "Base case"
  } else if (grepl(".berlin", value, fixed = TRUE)) {
    "Scenario 2"
  } else if (grepl(".city", value, fixed = TRUE)) {
    "Scenario 3"
  } else {
    stop(paste("Unexpected value:", value, "only values which contain '.base', '.berlin', '.city' are supported", sep = " "))
  }
}

get_name_2 <- function(value) {
  if (grepl("Base Case", value, fixed = TRUE)) {
    "Base case"
  } else if (grepl("Berlin", value, fixed = TRUE)) {
    "Scenario 2"
  } else if (grepl("City Center", value, fixed = TRUE)) {
    "Scenario 3"
  } else if (grepl("All", value, fixed = TRUE)) {
    "Scenario 1"
  } else {
    stop(paste("Unexpected value:", value, "only values which contain '.base', '.berlin', '.city' are supported", sep = " "))
  }
}


#---------------------- Concentration Plots start here -------------
palm_base <- read_csv("/Users/janek/repos/shared-svn/projects/mosaik-2/doc/40_publications/mosaik-2-02/data-files/scenarios/base-case/photoshade_6km10m_lod2_av_masked_M01.day2-si-units.xyt.csv") %>%
  mutate(hour = time / 3600) %>%
  filter(hour < 24)

palm_berlin <- read_csv("/Users/janek/repos/shared-svn/projects/mosaik-2/doc/40_publications/mosaik-2-02/data-files/scenarios/rp-berlin/photoshade_6km10m_berlin_av_masked_M01.day2-si-units.xyt.csv") %>%
  mutate(hour = time / 3600) %>%
  filter(hour < 24)

palm_city <- read_csv("/Users/janek/repos/shared-svn/projects/mosaik-2/doc/40_publications/mosaik-2-02/data-files/scenarios/rp-city/photoshade_6km10m_hundek_av_masked_M01.day2-si-units.xyt.csv") %>%
  mutate(hour = time / 3600) %>%
  filter(hour < 24)

base_nox <- palm_base %>% select(hour, x, y, NOx)
berlin_nox <- palm_berlin %>% select(hour, x, y, NOx)
city_nox <- palm_city %>% select(hour, x, y, NOx)

joined_nox <- base_nox %>%
  inner_join(berlin_nox, by = c("hour", "x", "y"), suffix = c("", ".berlin")) %>%
  inner_join(city_nox, by = c("hour", "x", "y"), suffix = c(".base", ".city")) %>%
  pivot_longer(cols = c(ends_with(".berlin"), ends_with(".base"), ends_with(".city")), values_to = "NOx",) %>%
  mutate(scenario_name = sapply(name, get_name_1)) %>%
  mutate(NOx = NOx * 1e6)

base_pm <- palm_base %>% select(hour, x, y, PM10)
berlin_pm <- palm_berlin %>% select(hour, x, y, PM10)
city_pm <- palm_city %>% select(hour, x, y, PM10)

joined_pm <- base_pm %>%
  inner_join(berlin_pm, by = c("hour", "x", "y"), suffix = c("", ".berlin")) %>%
  inner_join(city_pm, by = c("hour", "x", "y"), suffix = c(".base", ".city")) %>%
  pivot_longer(cols = c(ends_with(".berlin"), ends_with(".base"), ends_with(".city")), values_to = "PM",) %>%
  mutate(scenario_name = sapply(name, get_name_1)) %>%
  mutate(PM = PM * 1e6)

#------------------------ write diff csv ------------------------------------------------------------
diffs_nox <- berlin_nox %>%
  inner_join(city_nox, by = c("hour", "x", "y"), suffix = c(".berlin", ".city")) %>%
  pivot_longer(cols = c(ends_with(".berlin"), ends_with(".city")), values_to = "NOx") %>%
  left_join(base_nox, by = c("hour", "x", "y"), suffix = c("", ".base")) %>%
  mutate(diff = NOx - NOx.base)

diff_nox_berlin <- diffs_nox %>%
  filter(name == "NOx.berlin") %>%
  select(hour, x, y, diff)
write_csv(diff_nox_berlin, "/Users/janek/repos/shared-svn/projects/mosaik-2/doc/40_publications/mosaik-2-02/data-files/scenarios/rp-berlin/berlin-diff-nox.xyt.csv")

diff_nox_city <- diffs_nox %>%
  filter(name == "NOx.city") %>%
  select(hour, x, y, diff)
write_csv(diff_nox_city, "/Users/janek/repos/shared-svn/projects/mosaik-2/doc/40_publications/mosaik-2-02/data-files/scenarios/rp-city/city-diff-nox.xyt.csv")

#------------------------ create box plots for hourly emissions in base case -------------------------
pm_factor <- 10

base_all_no <- palm_base %>%
  select(hour, x, y, NO, NO2, NOx, PM10) %>%
  mutate(PM = PM10 * pm_factor) %>%
  pivot_longer(cols = c(NO, NO2, NOx, PM), names_to = "species", values_to = "concentrations") %>%
  mutate(concentrations = concentrations * 1e6)

p <- ggplot(base_all_no, aes(x = factor(hour), y = concentrations, color = factor(species))) +
  geom_boxplot(outlier.shape = NaN) +
  ggtitle("PALM-4U concentrations") +
  labs(color = "Species") +
  xlab("Hour") +
  ylab("") +
  scale_y_continuous(
    limits = c(0, 120),
    name = "NOx concentrations [\u00B5g/m3]",
    sec.axis = sec_axis(~. / pm_factor, name = "PM concentrations [\u00B5g/m3]")
  ) +
  scale_fill_manual(values = cbPalette) +
  scale_color_manual(values = cbPalette) +
  theme_light(base_size = 8)
p
ggsave(plot = p, filename = "/Users/janek/Documents/writing/mosaik-2-05/data-files/r-output/box-base.pdf", width = 183, height = 80, units = "mm")
ggsave(plot = p, filename = "/Users/janek/Documents/writing/mosaik-2-05/data-files/r-output/box-base.png", width = 183, height = 80, units = "mm", dpi = 600)

# base_pm <- palm_base %>%
#   pivot_longer(cols = c(PM10), names_to = "species", values_to = "concentrations") %>%
#   mutate(concentrations = concentrations * 1e6)
#
# p <- ggplot(base_pm, aes(x = factor(hour), y = concentrations, color = factor(species))) +
#   geom_boxplot(outlier.shape = NaN) +
#   ylim(0, 7.5) +
#   ggtitle("PALM-4U concentrations PM10 [\u00B5g/m3]") +
#   labs(color = "Species") +
#   xlab("Hour") +
#   ylab("") +
#   scale_fill_manual(values = cbPalette) +
#   scale_color_manual(values = cbPalette) +
#   theme_light()
# p
# ggsave(
#   plot = p,
#   filename = "/Users/janek/Documents/writing/mosaik-2-05/data-files/r-output/box-pm-base.pdf",
#   width = 220,
#   height = 118,
#   units = "mm"
# )
# ggsave(
#   plot = p,
#   filename = "/Users/janek/Documents/writing/mosaik-2-05/data-files/r-output/box-pm-base.png",
#   width = 220,
#   height = 118,
#   units = "mm"
# )

#------------------------ create box plots for comparison of scenarios ------------------------ 

p <- ggplot(joined_nox, aes(x = factor(hour), y = NOx, color = factor(scenario_name))) +
  geom_boxplot(outlier.shape = NaN) +
  ylim(0, 125) +
  ggtitle("NOx concentrations [\u00B5g/m3] by scenario") +
  labs(color = "Scenario") +
  xlab("Hour") +
  ylab("") +
  scale_fill_manual(values = scenario_color_palette) +
  scale_color_manual(values = scenario_color_palette) +
  theme_light(base_size = 8)
p
ggsave(plot = p, filename = "/Users/janek/Documents/writing/mosaik-2-05/data-files/r-output/box-nox-compare.pdf", width = 183, height = 80, units = "mm")
ggsave(plot = p, filename = "/Users/janek/Documents/writing/mosaik-2-05/data-files/r-output/box-nox-compare.png", width = 183, height = 80, units = "mm")

p <- ggplot(joined_pm, aes(x = factor(hour), y = PM, color = factor(scenario_name))) +
  geom_boxplot(outlier.shape = NaN) +
  ylim(0, 7.5) +
  ggtitle("PM concentrations [\u00B5g/m3] by scenario") +
  labs(color = "Scenario") +
  xlab("Hour") +
  ylab("") +
  scale_fill_manual(values = scenario_color_palette) +
  scale_color_manual(values = scenario_color_palette) +
  theme_light(base_size = 8)
p
ggsave(plot = p, filename = "/Users/janek/Documents/writing/mosaik-2-05/data-files/r-output/box-pm-compare.pdf", width = 183, height = 103, units = "mm")
ggsave(plot = p, filename = "/Users/janek/Documents/writing/mosaik-2-05/data-files/r-output/box-pm-compare.png", width = 183, height = 103, units = "mm")

nox_medians <- joined_nox %>%
  group_by(hour, name) %>%
  summarise(median(NOx))

pm_medians <- joined_pm %>%
  group_by(hour, name) %>%
  summarise(median(PM))

nox_diffs <- joined_nox %>%
  pivot_wider(id_cols = c("hour", "x", "y"), names_from = name, values_from = NOx) %>%
  group_by(hour) %>%
  summarise(median_base = median(NOx.base), median_berlin = median(NOx.berlin), median_city = median(NOx.city)) %>%
  mutate(diff_median_berlin = median_base - median_berlin, diff_median_city = median_base - median_city) %>%
  select(hour, diff_median_berlin, diff_median_city) %>%
  pivot_longer(cols = c("diff_median_berlin", "diff_median_city"), names_to = "name", values_to = "diff_to_base")

p <- ggplot(nox_diffs, aes(x = hour, y = diff_to_base, color = name)) +
  geom_line() +
  geom_point() +
  theme_light()
p

nox_diffs_relative <- joined_nox %>%
  pivot_wider(id_cols = c("hour", "x", "y"), names_from = name, values_from = NOx) %>%
  group_by(hour) %>%
  summarise(median_base = median(NOx.base), median_berlin = median(NOx.berlin), median_city = median(NOx.city)) %>%
  mutate(diff_median_berlin = median_berlin / median_base, diff_median_city = median_city / median_base) %>%
  select(hour, diff_median_berlin, diff_median_city) %>%
  pivot_longer(cols = c("diff_median_berlin", "diff_median_city"), names_to = "name", values_to = "diff_to_base")

p <- ggplot(nox_diffs_relative, aes(x = hour, y = diff_to_base, color = name)) +
  geom_line() +
  geom_point() +
  theme_light()
p
#------------------------ create emission mass plot ------------------------

# skip this one for the journal paper

# base_factors <- palm_base %>%
#   select(hour, x, y, NOx, PM10) %>%
#   mutate(mass_NOx = NOx * 10 * 10 * 10, mass_PM10 = PM10 * 10 * 10 * 10) %>%
#   group_by(hour) %>%
#   summarise(factor_NOx = sum(mass_NOx) / 26428.476, factor_PM10 = sum(mass_PM10) / 1228.3311) %>%
#   pivot_longer(cols = starts_with("factor_"), values_to = "factor", names_to = "species", names_prefix = "factor_")
#
# p <- ggplot(base_factors, aes(hour, factor, color = species)) +
#   geom_line() +
#   ggtitle("Normalized mass of pollutants") +
#   labs(color = "species") +
#   xlab("hour") +
#   ylab("") +
#   scale_color_manual(values = cbPalette) +
#   scale_color_manual(values = cbPalette) +
#   theme_light()
# p
# ggsave(plot = p, filename = "/Users/janek/Documents/writing/mosaik-2-05/data-files/r-output/line-normalized-sums.pdf", width = 220, height = 118, units = "mm")
# ggsave(plot = p, filename = "/Users/janek/Documents/writing/mosaik-2-05/data-files/r-output/line-normalized-sums.png", width = 220, height = 118, units = "mm")

#------------------------ create toll plot ------------------------ 

base_toll <- palm_base %>%
  select(hour, x, y, NOx, PM10) %>%
  mutate(mass_NOx = NOx * 10 * 10 * 10, mass_PM10 = PM10 * 10 * 10 * 10) %>%
  group_by(hour) %>%
  summarise(factor_NOx = sum(mass_NOx) / 26428.476, factor_PM10 = sum(mass_PM10) / 1228.3311) %>%
  mutate(toll_NOx = factor_NOx * 36.8 / 1000 * 0.338 / 1000, toll_PM10 = factor_PM10 * 36.8 / 1000 * 0.002 / 1000) %>%
  mutate(total_toll = toll_NOx + toll_PM10) %>%
  # we increased the toll by a factor of 100 and then we want it per km
  mutate(total_toll = total_toll * 100 * 1000)

p_toll <- ggplot(base_toll, aes(hour, total_toll)) +
  geom_line(color = "#4285f4") +
  geom_point(color = "#4285f4", size = 0.75) +
  ggtitle("Charged toll by the hour [EUR/km]") +
  xlab("Hour") +
  ylab("") +
  theme_light(base_size = 8)
p_toll
# ggsave(plot = p, filename = "/Users/janek/Documents/writing/mosaik-2-05/data-files/r-output/line-toll.pdf", width = 140, height = 79, units = "mm")
# ggsave(plot = p, filename = "/Users/janek/Documents/writing/mosaik-2-05/data-files/r-output/line-toll.png", width = 140, height = 79, units = "mm")

# ------------------ Print modal shares for different scenarios ---------------
share_by_hour <- read_csv("/Users/janek/Documents/berlin_roadpricing/modal-split-hour.csv")
only_car <- share_by_hour %>%
  filter(time < 86400) %>%
  filter(mode == "car") %>%
  mutate(hour = time / 3600) %>%
  mutate(scenario_name = sapply(name, get_name_2))

p_modal_share <- ggplot(only_car, aes(x = hour, y = value, color = scenario_name)) +
  geom_line() +
  geom_point(size = 0.75) +
  ggtitle("Car trips within Berlin City Center") +
  labs(color = "Scenario") +
  xlab("Hour") +
  ylab("") +
  scale_color_manual(values = scenario_color_palette) +
  theme_light(base_size = 8)
p_modal_share

p_combined <- p_toll + p_modal_share
ggsave(p_combined, filename = "/Users/janek/Documents/writing/mosaik-2-05/data-files/r-output/toll_modal_share_combined.pdf", width = 183, height = 80, units = "mm")
ggsave(p_combined, filename = "/Users/janek/Documents/writing/mosaik-2-05/data-files/r-output/toll_modal_share_combined.png", width = 183, height = 80, units = "mm", dpi = 300)

# Include diff plots of the car trips, to retreive numbers for the written report
car_diffs_relative <- only_car %>%
  pivot_wider(id_cols = c("time", "hour"), names_from = scenario_name, values_from = value) %>%
  mutate(diff_scenario_1 = `Scenario 1` / `Base case`) %>%
  mutate(diff_scenario_2 = `Scenario 2` / `Base case`) %>%
  mutate(diff_scenario_3 = `Scenario 3` / `Base case`) %>%
  select(time, hour, diff_scenario_1, diff_scenario_2, diff_scenario_3) %>%
  pivot_longer(cols = c("diff_scenario_1", "diff_scenario_2", "diff_scenario_3"), names_to = "name", values_to = "value")

p <- ggplot(car_diffs_relative, aes(x = hour, y = value, color = name)) +
  geom_line() +
  theme_light()
p

car_diffs_absolute <- only_car %>%
  pivot_wider(id_cols = c("time", "hour"), names_from = scenario_name, values_from = value) %>%
  mutate(diff_scenario_1 = `Base case` - `Scenario 1`) %>%
  mutate(diff_scenario_2 = `Base case` - `Scenario 2`) %>%
  mutate(diff_scenario_3 = `Base case` - `Scenario 3`) %>%
  select(time, hour, diff_scenario_1, diff_scenario_2, diff_scenario_3) %>%
  pivot_longer(cols = c("diff_scenario_1", "diff_scenario_2", "diff_scenario_3"), names_to = "name", values_to = "value")

p <- ggplot(car_diffs_absolute, aes(x = hour, y = value, color = name)) +
  geom_line() +
  geom_point() +
  theme_light()
p

#----------------------- create linear fit plot
# read in smoothed matsim data
matsim_smoothed <- read_csv("/Users/janek/repos/shared-svn/projects/mosaik-2/doc/40_publications/mosaik-2-02/data-files/scenarios/base-case/berlin-with-geometry-attributes.output_smoothed_rastered.xyt.csv") %>%
  mutate(hour = time / 3600)

matsim_palm_base <- palm_base %>%
  inner_join(matsim_smoothed, by = c("x", "y", "hour"), suffix = c(".palm", ".matsim")) %>%
  filter(hour > 5) %>%
  filter(hour < 18) %>%
  mutate(NOx.matsim = NOx.matsim * 1e6) %>%
  mutate(NOx.palm = NOx.palm * 1e6) %>%
  select(hour, x, y, NOx.matsim, NOx.palm)

#Our transformation function
scaleFUN <- function(x) sprintf("%.2f", x)

p <- ggplot(matsim_palm_base, aes(NOx.matsim, y = NOx.palm)) +
  geom_point(shape = ".") +
  geom_smooth(method = "lm") +
  ylim(0, 500) +
  xlim(0, 10000) +
  ggtitle("NOx concentrations [\u00B5g/m3] MATSim vs. PALM-4U") +
  facet_wrap(vars(hour)) +
  scale_fill_manual(values = cbPalette) +
  theme_light(base_size = 8) +
  theme(
    strip.text = element_text(size = 6, margin = margin(t = 2, b = 2))
  )
p
ggsave(plot = p, filename = "/Users/janek/Documents/writing/mosaik-2-05/data-files/r-output/linear-fit-nox.pdf", width = 183, height = 103, units = "mm")
ggsave(plot = p, filename = "/Users/janek/Documents/writing/mosaik-2-05/data-files/r-output/linear-fit-nox.png", width = 183, height = 103, units = "mm")

# ------------------------ Tolls charged

tolls_charged <- read_csv("/Users/janek/Documents/berlin_roadpricing/sum-hour.csv")

ggplot(tolls_charged, aes(x = time, y = sum * -1, color = name)) +
  geom_line() +
  geom_point() +
  theme_light()

