library(tidyverse)
library(dplyr)

csv_data <- read_csv("C:/Users/janek/repos/runs-svn/mosaik-2/berlin/mosaik-2-berlin-with-geometry-attributes/linear-fit-data-NO2.csv")
csv_data

subset <- head(csv_data, 1000)
subset

ggplot(data = subset) +
  geom_point(mapping = aes(x = x, y = y)) +
  geom_smooth(mapping = aes(x = x, y = y))