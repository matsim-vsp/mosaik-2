library(tidyverse)

cbPalette <- c("#4285f4", "#ea4335", "#fbbc04", "#34a853", "#ff6d01", "#46bdc6", "#7baaf7", "#f07b72", "#fcd04f", "#71c287")
intersecting_trips <- read_csv("C:/Users/Janekdererste/Documents/work/berlin-roadpricing/output_roadpricing/trips-intersecting.csv")

filtered <- intersecting_trips %>%
  filter(mode != "ride") %>%
  filter(mode != "freight") %>%
  mutate(hour = floor(time / 3600))

split_by_hour <- filtered %>% count(hour, mode)

p <- ggplot(split_by_hour, aes(x = hour)) +
  geom_line(aes(y = n, color = mode)) +
  facet_wrap(vars(mode)) +
  scale_color_manual(values = cbPalette) +
  theme_light()
p