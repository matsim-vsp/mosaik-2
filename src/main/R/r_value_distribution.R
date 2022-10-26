library(tidyverse)
library(dplyr)

csv_data <- read_csv("C:/Users/janek/repos/runs-svn/mosaik-2/berlin/mosaik-2-berlin-with-geometry-attributes/palm-output/photoshade_6km10m_lod2_av_masked_M01.day2-PM10-r-values.xyt.csv")
csv_data

ggplot(csv_data, aes(x = value)) +
  geom_histogram(binwidth = 1)

csv_data %>%
  summarise(across(where(is.numeric), mean))

csv_data %>%
  summarise(across(where(is.numeric), median))
#facet_grid(rows = vars(type), scales = "free")

#csv_data1 = csv_data %>% mutate(intervall = cut(value, breaks = 2))
#ggplot(csv_data1, aes(intervall)) + geom_bar()