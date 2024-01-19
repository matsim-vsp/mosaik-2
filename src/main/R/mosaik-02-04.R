# add tidyverse and custom color palette which looks like google spread sheets
library(tidyverse)
cbPalette <- c("#4285f4", "#ea4335", "#fbbc04", "#34a853", "#ff6d01", "#46bdc6", "#7baaf7", "#f07b72", "#fcd04f", "#71c287")

get_name_1 <- function(value) {
  if (grepl(".base", value, fixed = TRUE)) {
    "Base Case"
  } else if (grepl(".berlin", value, fixed = TRUE)) {
    "Scenario 1"
  } else if (grepl(".city", value, fixed = TRUE)) {
    "Scenario 2"
  } else {
    stop(paste("Unexpected value:", value, "only values which contain '.base', '.berlin', '.city' are supported", sep = " "))
  }
}

get_name_2 <- function(value) {
  if (grepl("Base Case", value, fixed = TRUE)) {
    "Base Case"
  } else if (grepl("Berlin", value, fixed = TRUE)) {
    "Scenario 1"
  } else if (grepl("City Center", value, fixed = TRUE)) {
    "Scenario 2"
  } else if (grepl("All", value, fixed = TRUE)) {
    "Scenario 0"
  } else {
    stop(paste("Unexpected value:", value, "only values which contain '.base', '.berlin', '.city' are supported", sep = " "))
  }
}

# ------------------ Print modal shares for different scenarios ---------------
share_by_hour <- read_csv("/Users/janek/Documents/berlin_roadpricing/modal-split-hour.csv")
only_car <- share_by_hour %>%
  filter(time < 86400) %>%
  filter(mode == "car") %>%
  mutate(hour = time / 3600) %>%
  mutate(scenario_name = sapply(name, get_name_2))

p <- ggplot(only_car, aes(x = hour)) +
  geom_line(aes(y = value, color = scenario_name)) +
  ggtitle("Car trips within Berlin - Scenario comparison") +
  labs(color = "Scenario") +
  xlab("Hour") +
  ylab("") +
  scale_color_manual(values = cbPalette) +
  theme_light()

ggsave(plot = p, filename = "/Users/janek/Documents/writing/mosaik-2-04-hHEART24/data-files-nextcloud/r-output/car-trips-berlin.pdf", width = 220, height = 118, units = "mm")
ggsave(plot = p, filename = "/Users/janek/Documents/writing/mosaik-2-04-hHEART24/data-files-nextcloud/r-output/car-trips-berlin.png", width = 220, height = 118, units = "mm", dpi = 300)


#---------------------- Concentration Plots start here -------------
palm_base <- read_csv("/Users/janek/Documents/writing/mosaik-2-02/data-files-nextcloud/scenarios/base-case/photoshade_6km10m_lod2_av_masked_M01.day2-si-units.xyt.csv") %>%
  mutate(hour = time / 3600) %>%
  filter(hour < 24)

palm_berlin <- read_csv("/Users/janek/Documents/writing/mosaik-2-02/data-files-nextcloud/scenarios/rp-berlin/photoshade_6km10m_berlin_av_masked_M01.day2-si-units.xyt.csv") %>%
  mutate(hour = time / 3600) %>%
  filter(hour < 24)

palm_city <- read_csv("/Users/janek/Documents/writing/mosaik-2-02/data-files-nextcloud/scenarios/rp-city/photoshade_6km10m_hundek_av_masked_M01.day2-si-units.xyt.csv") %>%
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

#------------------------ write diff csv ------------------------------------------------------------
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

#------------------------ create box plots for hourly emissions in base case -------------------------
base_all_no <- palm_base %>%
  select(hour, x, y, NO, NO2, NOx) %>%
  pivot_longer(cols = c(NO, NO2, NOx), names_to = "species", values_to = "concentrations") %>%
  mutate(concentrations = concentrations * 1e6)

p <- ggplot(base_all_no, aes(x = factor(hour), y = concentrations, color = factor(species))) +
  geom_boxplot(outlier.shape = NaN) +
  ylim(0, 150) +
  ggtitle("PALM NOx concentrations - Base Case [\u00B5g/m3]") +
  labs(color = "Species") +
  xlab("Hour") +
  ylab("") +
  scale_fill_manual(values = cbPalette) +
  scale_color_manual(values = cbPalette) +
  theme_light()
p
ggsave(plot = p, filename = "/Users/janek/Documents/writing/mosaik-2-04-hHEART24/data-files-nextcloud/r-output/box-nox-base.pdf", width = 220, height = 118, units = "mm")
ggsave(plot = p, filename = "/Users/janek/Documents/writing/mosaik-2-04-hHEART24/data-files-nextcloud/r-output/box-nox-base.png", width = 220, height = 118, units = "mm", dpi = 300)

base_pm <- palm_base %>%
  pivot_longer(cols = c(PM10), names_to = "species", values_to = "concentrations") %>%
  mutate(concentrations = concentrations * 1e6)

p <- ggplot(base_pm, aes(x = factor(hour), y = concentrations, color = factor(species))) +
  geom_boxplot(outlier.shape = NaN) +
  ylim(0, 7.5) +
  ggtitle("PALM PM10 concentrations - Base Case [\u00B5g/m3]") +
  labs(color = "Species") +
  xlab("Hour") +
  ylab("") +
  scale_fill_manual(values = cbPalette) +
  scale_color_manual(values = cbPalette) +
  theme_light()
p
ggsave(
  plot = p,
  filename = "/Users/janek/Documents/writing/mosaik-2-04-hHEART24/data-files-nextcloud/r-output/box-pm-base.pdf",
  width = 220,
  height = 118,
  units = "mm"
)
ggsave(
  plot = p,
  filename = "/Users/janek/Documents/writing/mosaik-2-04-hHEART24/data-files-nextcloud/r-output/box-pm-base.png",
  width = 220,
  height = 118,
  units = "mm"
)

#------------------------ create box plots for comparison of scenarios ------------------------ 

p <- ggplot(joined_nox, aes(x = factor(hour), y = NOx, color = factor(scenario_name))) +
  geom_boxplot(outlier.shape = NaN) +
  ylim(0, 150) +
  ggtitle("NOx concentrations [\u00B5g/m3] - Scenario comparison") +
  labs(color = "Scenario") +
  xlab("Hour") +
  ylab("") +
  scale_fill_manual(values = cbPalette) +
  scale_color_manual(values = cbPalette) +
  theme_light()
p
ggsave(plot = p, filename = "/Users/janek/Documents/writing/mosaik-2-04-hHEART24/data-files-nextcloud/r-output/box-nox-compare.pdf", width = 220, height = 118, units = "mm")
ggsave(plot = p, filename = "/Users/janek/Documents/writing/mosaik-2-04-hHEART24/data-files-nextcloud/r-output/box-nox-compare.png", width = 220, height = 118, units = "mm")

#------------------------ create emission mass plot ------------------------ 

base_factors <- palm_base %>%
  select(hour, x, y, NOx, PM10) %>%
  mutate(mass_NOx = NOx * 10 * 10 * 10, mass_PM10 = PM10 * 10 * 10 * 10) %>%
  group_by(hour) %>%
  summarise(factor_NOx = sum(mass_NOx) / 26428.476, factor_PM10 = sum(mass_PM10) / 1228.3311) %>%
  pivot_longer(cols = starts_with("factor_"), values_to = "factor", names_to = "species", names_prefix = "factor_")

p <- ggplot(base_factors, aes(hour, factor, color = species)) +
  geom_line() +
  ggtitle("Normalized pollution mass") +
  labs(color = "Species") +
  xlab("Hour") +
  ylab("") +
  scale_color_manual(values = cbPalette) +
  scale_color_manual(values = cbPalette) +
  theme_light()
p
ggsave(plot = p, filename = "/Users/janek/Documents/writing/mosaik-2-04-hHEART24/data-files-nextcloud/r-output/line-normalized-sums.pdf", width = 220, height = 118, units = "mm")
ggsave(plot = p, filename = "/Users/janek/Documents/writing/mosaik-2-04-hHEART24/data-files-nextcloud/r-output/line-normalized-sums.png", width = 220, height = 118, units = "mm")

#------------------------ create toll plot ------------------------ 

base_toll <- palm_base %>%
  select(hour, x, y, NOx, PM10) %>%
  mutate(mass_NOx = NOx * 10 * 10 * 10, mass_PM10 = PM10 * 10 * 10 * 10) %>%
  group_by(hour) %>%
  summarise(factor_NOx = sum(mass_NOx) / 26428.476, factor_PM10 = sum(mass_PM10) / 1228.3311) %>%
  mutate(toll_NOx = factor_NOx * 36.8 / 1000 * 0.338 / 1000, toll_PM10 = factor_PM10 * 36.8 / 1000 * 0.002 / 1000) %>%
  mutate(total_toll = toll_NOx + toll_PM10) %>%
  mutate(total_toll = total_toll * 100000)

p <- ggplot(base_toll, aes(hour, total_toll)) +
  geom_line(color = "#4285f4") +
  ggtitle("Toll rates of one day [EUR/km]") +
  xlab("Hour") +
  ylab("") +
  theme_light()
p
ggsave(plot = p, filename = "/Users/janek/Documents/writing/mosaik-2-04-hHEART24/data-files-nextcloud/r-output/line-toll.pdf", width = 220, height = 118, units = "mm")
ggsave(plot = p, filename = "/Users/janek/Documents/writing/mosaik-2-04-hHEART24/data-files-nextcloud/r-output/line-toll.png", width = 220, height = 118, units = "mm")

#----------------------- create linear fit plot
# read in smoothed matsim data
matsim_smoothed <- read_csv("/Users/janek/Documents/writing/mosaik-2-02/data-files-nextcloud/scenarios/base-case/berlin-with-geometry-attributes.output_smoothed_rastered.xyt.csv") %>%
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
  ggtitle("NOx concentrations [\u00B5g/m3] MATSim vs. PALM") +
  facet_wrap(vars(hour)) +
  scale_fill_manual(values = cbPalette) +
  theme_light()
p
ggsave(plot = p, filename = "/Users/janek/Documents/writing/mosaik-2-04-hHEART24/data-files-nextcloud/r-output/linear-fit-nox.pdf", width = 220, height = 118, units = "mm")
ggsave(plot = p, filename = "/Users/janek/Documents/writing/mosaik-2-04-hHEART24/data-files-nextcloud/r-output/linear-fit-nox.png", width = 220, height = 118, units = "mm")