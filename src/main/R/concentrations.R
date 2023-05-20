library(tidyverse)

# this finds values above 0.0005 but not the ones within bounding box [383546.5, 383566.5, 5817499.0, 5817509.0]
is_outlier <- function(value, x, y) {
  return(value > 0.0005 & !(x >= 383546.5 &
    x <= 383566.5 &
    y >= 5817499.0 &
    y <= 5817509.0))
}

print("Start reading csv.")
csv_data <- read_csv("C:/Users/janek/Documents/work/palm/berlin_with_geometry_attributes/palm-output/photoshade_6km10m_lod2_av_masked_M01.day2-si-units.xyt.csv")

print("add hour column")
data_hr <- mutate(csv_data, hour = time / 3600)

outlier <- data_hr %>% filter(is_outlier(NO2, x, y))
write_csv(outlier, "C:/Users/janekdererste/repos/runs-svn/mosaik-2/berlin/mosaik-2-berlin-with-geometry-attributes/palm-output/photoshade_6km10m_lod2_av_masked_M01.day2-si-units-NO2_outlier.xyt.csv")

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

p <- ggplot(nox_and_o3, aes(x = factor(hour), y = concentration, color = factor(species))) +
  ylim(0, 1e-4) +
  geom_boxplot(outlier.alpha = 0.3, outlier.shape = ".") +
  ggtitle("PALM Concentrations [g/m3]")
ggsave(plot = p, filename = "nox_and_o3_box.png", width = 16, height = 9)

p <- ggplot(nox, aes(x = factor(hour), y = concentration, color = factor(species))) +
  ylim(0, 1e-4) +
  geom_boxplot(outlier.alpha = 0.3, outlier.shape = ".") +
  ggtitle("PALM Concentrations [g/m3]")
ggsave(plot = p, filename = "nox_box.png", width = 16, height = 9)

p <- ggplot(pm, aes(x = factor(hour), y = concentration, color = factor(species))) +
  ylim(0, 1e-5) +
  geom_boxplot(outlier.alpha = 0.3, outlier.shape = ".") +
  ggtitle("PALM Concentrations [g/m3]")
ggsave(plot = p, filename = "pm_box.png", width = 16, height = 9)

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