library(tidyverse)
library(dplyr)

csv_data <- read_csv("C:/Users/janek/repos/runs-svn/mosaik-2/berlin/mosaik-2-berlin-with-geometry-attributes/output/berlin-with-geometry-attributes.output_emission_raster-NO2.xyt.csv")
csv_data

ggplot(csv_data, aes(x = value)) +
  geom_histogram(bins = 1000)

csv_data %>%
  summarise(across(where(is.numeric), mean))

csv_data %>%
  summarise(across(where(is.numeric), median))

ggplot(csv_data, aes(y = value, ymax = 0.01)) +
  geom_boxplot(outlier.alpha = 0.1)