memory.limit(size=20000)
library(tidyverse)

# this finds values above 0.0005 but not the ones within bounding box [383546.5, 383566.5, 5817499.0, 5817509.0]
is_outlier <- function(value, x, y)  {
  return (value > 0.0005 & !(x >= 383546.5 & x <= 383566.5 & y >= 5817499.0 & y <= 5817509.0))
}

print("Start reading csv.")
csv_data <- read_csv("C:/Users/janek/repos/runs-svn/mosaik-2/berlin/mosaik-2-berlin-with-geometry-attributes/palm-output/photoshade_6km10m_lod2_av_masked_M01.day2-si-units-NO2.xyt.csv")

print("add hour column")
data_hr <- mutate(csv_data, hour = time / 3600)

outlier <- data_hr %>% filter(is_outlier(value, x, y))
write_csv(outlier, "C:/Users/janek/repos/runs-svn/mosaik-2/berlin/mosaik-2-berlin-with-geometry-attributes/palm-output/photoshade_6km10m_lod2_av_masked_M01.day2-si-units-NO2_outlier.xyt.csv")

print("filter outlier from main dataset")
data_hr_no_outlier <- data_hr %>% dplyr::anti_join(outlier, by = c("x", "y"))
write_csv(data_hr_no_outlier, "C:/Users/janek/repos/runs-svn/mosaik-2/berlin/mosaik-2-berlin-with-geometry-attributes/palm-output/no-outlier-NO2.xyt.csv")

print("create box plot")
p <- ggplot(data_hr_no_outlier, aes(hour, value)) +
  geom_boxplot(outlier.alpha = 0.5, aes(group = cut_width(hour, 1))) +
  ggtitle("PALM-NO2 Concentrations [g/m3]")
p
ggsave(plot=p, filename = "box-plot-no2-concentrations.png", width=16, height=9)

p <- ggplot (data_hr_no_outlier, aes(hour, value)) +
  geom_boxplot(outlier.shape = NA, aes(group = cut_width(hour, 1))) +
  ylim(0, 0.0001) +
  ggtitle("PALM-NO2 Concentrations [g/m3] Cut Y-slab")
p
ggsave(plot=p, filename = "box-plot-no2-concentrations-no-outlier.png", width=16, height=9)