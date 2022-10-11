library(tidyverse)
library(dplyr)

csv_data <- read_csv("X:/mosaik-2-r-estimation/photoshade_6km10m_lod2_av_masked_M01.day2-NO2-r-values.csv")
csv_data

ggplot(csv_data, aes(x = value)) +
  geom_histogram(binwidth = 1)

csv_data %>%
  summarise(across(where(is.numeric), mean))
#facet_grid(rows = vars(type), scales = "free")

#csv_data1 = csv_data %>% mutate(intervall = cut(value, breaks = 2))
#ggplot(csv_data1, aes(intervall)) + geom_bar()