library(tidyverse)

cbPalette <- c("#4285f4", "#ea4335", "#fbbc04", "#34a853", "#ff6d01", "#46bdc6", "#7baaf7", "#f07b72", "#fcd04f", "#71c287")

# this finds values above 0.0005 but not the ones within bounding box [383546.5, 383566.5, 5817499.0, 5817509.0]
is_outlier <- function(value, x, y) {
  return(value > 0.0005 & !(x >= 383546.5 &
    x <= 383566.5 &
    y >= 5817499.0 &
    y <= 5817509.0))
}

print("Start reading csv.")
csv_data <- read_csv("C:/Users/janek/Documents/work/berlin-roadpricing/output-rp-time-hundek-100/photoshade_6km10m_hundek_av_masked_M01.day2-si-units.xyt.csv")
csv_data

filter(csv_data, time == NaN)
print("add hour column")
data_hr <- mutate(csv_data, hour = time / 3600)

outlier <- data_hr %>% filter(is_outlier(NO2, x, y))
#write_csv(outlier, "C:/Users/janekdererste/repos/runs-svn/mosaik-2/berlin/mosaik-2-berlin-with-geometry-attributes/palm-output/photoshade_6km10m_lod2_av_masked_M01.day2-si-units-NO2_outlier.xyt.csv")

print("filter outlier from main dataset")
data_hr_no_outlier <- data_hr %>% dplyr::anti_join(outlier, by = c("x", "y"))
data_with_nox <- data_hr_no_outlier %>%
  mutate(NOx = NO + NO2) %>%
  pivot_longer(cols = c("NO2", "NO", "PM10", "O3", "NOx"), names_to = "species", values_to = "concentration")

nox_and_o3 <- data_with_nox %>%
  filter(species == "NOx" | species == "O3")

nox <- data_with_nox %>%
  filter(species == "NOx" |
           species == "NO" |
           species == "NO2")

pm <- data_with_nox %>%
  filter(species == "PM10")

p <- ggplot(nox, aes(x = factor(hour), y = concentration, color = factor(species))) +
  ylim(0, 5e-4) +
  geom_boxplot(outlier.alpha = 0.3, outlier.shape = ".") +
  ggtitle("PALM Concentrations [g/m3]") +
  scale_fill_manual(values = cbPalette) +
  theme_light()
ggsave(plot = p, filename = "nox_box.png", width = 3200, height = 1800, units = c("px"), dpi = 300)

p <- ggplot(pm, aes(x = factor(hour), y = concentration, color = factor(species))) +
  ylim(0, 3e-5) +
  geom_boxplot(outlier.alpha = 0.3, outlier.shape = ".") +
  ggtitle("PALM Concentrations [g/m3]") +
  scale_fill_manual(values = cbPalette) +
  theme_light()
ggsave(plot = p, filename = "pm_box.png", width = 3200, height = 1800, units = c("px"), dpi = 300)


# apply volume to convert  from concentration [g/m3] to [g], our volumes are 10m3
# use 1223.066 which is the highest value to set the toll to a factor of 1 for the highest value at 9am.
gpm <- pm %>%
  filter(species == "PM10") %>%
  mutate(value = concentration * 10 * 10 * 10) %>%
  group_by(hour) %>%
  summarise(time = time, value = sum(value), factor = sum(value) / 1223.066) %>%
  distinct(hour, .keep_all = TRUE)
gpm
write_csv(gpm, "./pm10_hourly_factors.csv")

# convert from concentration to mass
# then normalize the factor by dividing by the largest value which is in the data set.
gnox <- nox %>%
  filter(species == "NOx") %>%
  mutate(value = concentration * 10 * 10 * 10) %>%
  group_by(hour) %>%
  summarise(time = time, value = sum(value), factor = sum(value) / 26405.1841) %>%
  distinct(hour, .keep_all = TRUE)
gnox
write_csv(gnox, "./nox_hourly_factors.csv")

joined_factors <- gpm %>%
  inner_join(gnox, by = c("hour"), suffix = c(".pm", ".nox")) %>%
  select("hour", "factor.pm", "factor.nox") %>%
  pivot_longer(cols = starts_with("factor."), names_to = "species", values_to = "factor", names_prefix = "factor.")
joined_factors

p <- ggplot(joined_factors, aes(hour, factor, color = species)) +
  geom_line() +
  scale_color_manual(values = cbPalette) +
  theme_light() +
  ggtitle("Normalized Sums of Emissions from PALM simulation.") +
  labs(y = "Normalized sum of Emissions", x = "Hour of Day")
p
ggsave(plot = p, filename = "normalized_sums_pollutants_over_time.png", width = 8, height = 6)

tolls <- read_csv("./tolls.csv") %>%
  mutate(hour = time / 3600)
p <- ggplot(tolls, aes(hour, toll)) +
  geom_line(color = "#4285f4") +
  theme_light() +
  ggtitle("Toll over hour of day") +
  labs(y = "Toll [g/m]", x = "Hour of Day")
p
ggsave(plot = p, filename = "toll-over-hour.png", width = 8, height = 6)

matsim_sums <- read_csv("./sums.csv")

joined_sums_pm <- matsim_sums %>%
  filter(species == "PM10") %>%
  inner_join(gpm, by = "time", suffix = c(".matsim", ".palm")) %>%
  pivot_longer(cols = c("value.matsim", "value.palm")) %>%
  group_by(hour)

joined_sums_pm

p <- ggplot(joined_sums_pm, aes(x = hour, y = value, color = name)) +
  geom_line() +
  geom_point() +
  ggtitle("Sum of PM10 [g] in PALM Area")
ggsave(plot = p, filename = "pm10_sum.png", width = 16, height = 9)


joined_sums_nox <- matsim_sums %>%
  filter(species == "NOx") %>%
  inner_join(gnox, by = "time", suffix = c(".matsim", ".palm")) %>%
  pivot_longer(cols = c("value.matsim", "value.palm"))
joined_sums_nox

p <- ggplot(joined_sums_nox, aes(x = hour, y = value, color = name)) +
  geom_line() +
  geom_point() +
  ggtitle("Sum of NOx [g] in PALM Area")
ggsave(plot = p, filename = "nox_sum.png", width = 16, height = 9)

p <- ggplot(nox_and_o3, aes(x = factor(hour), y = concentration, color = factor(species))) +
  ylim(0, 1e-4) +
  geom_boxplot(outlier.alpha = 0.3, outlier.shape = ".") +
  ggtitle("PALM Concentrations [g/m3]")
ggsave(plot = p, filename = "nox_and_o3_box.png", width = 16, height = 9)


write_csv(data_hr_no_outlier, "C:/Users/janek/Documents/work/palm/berlin_with_geometry_attributes/palm-output/photoshade_6km10m_lod2_av_masked_M01.day2-si-units-no-outliers.xyt.csv")
longer <- data_hr_no_outlier %>%
  pivot_longer(cols = c("NO2", "NO", "PM10", "O3"), names_to = "species", values_to = "concentration")

head <- head(longer, n = 10000000)
p <- ggplot(longer, aes(x = factor(hour), y = concentration, color = factor(species))) +
  ylim(0, 1e-4) +
  geom_boxplot(outlier.alpha = 0.3, outlier.shape = ".")
ggsave(plot = p, filename = "box-plot-concentrations.png", width = 16, height = 9)

print("create box plot")
p <- ggplot(data_hr_no_outlier, aes(hour, NO2)) +
  geom_boxplot(outlier.alpha = 0.5, aes(group = cut_width(hour, 1))) +
  ylim(0, 0.0001) +
  ggtitle("PALM-NO2 Concentrations [g/m3]")

ggsave(plot = p, filename = "box-plot-no2-concentrations.png", width = 16, height = 9)

print("create box plot")
p <- ggplot(data_hr_no_outlier, aes(hour, PM10)) +
  geom_boxplot(outlier.alpha = 0.5, aes(group = cut_width(hour, 1))) +
  ggtitle("PALM-PM10 Concentrations [g/m3]")
ggsave(plot = p, filename = "box-plot-pm-concentrations.png", width = 16, height = 9)

print("create box plot")
p <- ggplot(data_hr_no_outlier, aes(hour, NO)) +
  geom_boxplot(outlier.alpha = 0.5, aes(group = cut_width(hour, 1))) +
  ggtitle("PALM-NO Concentrations [g/m3]")
ggsave(plot = p, filename = "box-plot-no-concentrations.png", width = 16, height = 9)

p <- ggplot(data_hr_no_outlier, aes(hour, value)) +
  geom_boxplot(outlier.shape = NA, aes(group = cut_width(hour, 1))) +
  ylim(0, 0.0001) +
  ggtitle("PALM-NO2 Concentrations [g/m3] Cut Y-slab")
ggsave(plot = p, filename = "box-plot-no2-concentrations-no-outlier.png", width = 16, height = 9)