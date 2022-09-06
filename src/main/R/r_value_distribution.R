library(tidyverse)
library(dplyr)

csv_data <- read_csv("C:/Users/Janekdererste/Desktop/photoshade_6km10m/r-values-by-type.csv")
csv_data

ggplot(csv_data, aes(x = value, color = type, fill = type)) +
  geom_histogram(binwidth = 1) +
  facet_grid(rows = vars(type), scales = "free")

#csv_data1 = csv_data %>% mutate(intervall = cut(value, breaks = 2))
#ggplot(csv_data1, aes(intervall)) + geom_bar()