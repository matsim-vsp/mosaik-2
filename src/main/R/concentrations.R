memory.limit(size=20000)

library(tidyverse)
library(dplyr)

print("Start reading csv.")
csv_data <- read_csv("C:/Users/janek/repos/runs-svn/mosaik-2/berlin/mosaik-2-berlin-with-geometry-attributes/palm-output/photoshade_6km10m_lod2_av_masked_M01.day2-si-units-NO2.xyt.csv")

print("add hour column")
data_hr <- mutate(csv_data, hour = time / 3600)
data_hr

outlier <- data_hr %>% filter(value > 0.0005)
write_csv(outlier, "C:/Users/janek/repos/runs-svn/mosaik-2/berlin/mosaik-2-berlin-with-geometry-attributes/palm-output/photoshade_6km10m_lod2_av_masked_M01.day2-si-units-NO2_outlier.xyt.csv")

print("create box plot")
p <- ggplot(data_hr, aes(hour, value)) +
  geom_boxplot(aes(group = cut_width(hour, 1))) +
  ggtitle("PALM-NO2 Concentrations [g/m3]")
p
ggsave(plot=p, filename = "box-plot-no2-concentrations.png", width=16, height=9)


p <- ggplot (data_hr, aes(hour, value)) +
  geom_boxplot(outlier.shape = NA, aes(group = cut_width(hour, 1))) +
  ylim(0, 0.0001) +
  ggtitle("PALM-NO2 Concentrations [g/m3] No Outlier")
p
ggsave(plot=p, filename = "box-plot-no2-concentrations-no-outlier.png", width=16, height=9)