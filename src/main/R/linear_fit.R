library(tidyverse)
library(dplyr)

csv_data <- read_csv("C:/Users/Janekdererste/repos/runs-svn/mosaik-2/berlin/mosaik-2-berlin-with-geometry-attributes/linear-fit-NO2.csv")

ggplot(csv_data) + geom_point()