library(tidyverse)

cbPalette <- c("#4285f4", "#ea4335", "#fbbc04", "#34a853", "#ff6d01", "#46bdc6", "#7baaf7", "#f07b72", "#fcd04f", "#71c287")
share_by_hour <- read_csv("/Users/janek/Documents/berlin_roadpricing/modal-split-hour.csv")

filtered <- share_by_hour %>%
  filter(time < 86400) %>%
  filter(mode != "ride") %>%
  filter(mode != "freight") %>%
  mutate(hour = time / 3600)

p <- ggplot(filtered, aes(mode, value)) +
  geom_bar(aes(fill = name), position = "dodge", stat = "identity") +
  facet_wrap(vars(hour)) +
  scale_fill_manual(values = cbPalette) +
  theme_light()
p



only_car  <- share_by_hour %>%
  filter(time < 86400) %>%
  filter(mode == "car") %>%
  mutate(hour = time / 3600)

p <- ggplot(only_car, aes(x = hour)) +
  geom_line(aes(y = value, color = name)) +
  ggtitle("Number of car trips within Berlin Boundaries") +
  scale_color_manual(values = cbPalette) +
  theme_light()
p
ggsave(plot = p, filename = "car-trips-over-hour.png", width = 3200, height = 1800, units = c("px"), dpi = 300)