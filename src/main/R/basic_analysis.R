library(tidyverse)

cbPalette <- c("#4285f4", "#ea4335", "#fbbc04", "#34a853", "#ff6d01", "#46bdc6", "#7baaf7", "#f07b72", "#fcd04f", "#71c287")
share_by_hour <- read_csv("C:/Users/janek/Documents/work/berlin-roadpricing/output_roadpricing/modal-split-hour-inside.csv")

filtered <- share_by_hour %>%
  filter(time < 86400) %>%
  filter(mode != "ride") %>%
  filter(mode != "freight") %>%
  mutate(hour = time / 3600)

runs_100 <- filtered %>%
  filter(name == "time-100" |
           name == "time-berlin-100" |
           name == "time-center-100" |
           name == "base-case")

p <- ggplot(filtered, aes(mode, value)) +
  geom_bar(aes(fill = name), position = "dodge", stat = "identity") +
  facet_wrap(vars(hour)) +
  scale_fill_manual(values = cbPalette) +
  theme_light()
p

ggplot(filtered, aes(x = hour,)) +
  geom_line(aes(y = value, color = mode)) +
  facet_wrap(vars(name)) +
  scale_color_manual(values = cbPalette) +
  theme_light()

ggplot(runs_100, aes(x = hour,)) +
  geom_line(aes(y = value, color = name)) +
  facet_wrap(vars(mode)) +
  scale_color_manual(values = cbPalette) +
  theme_light()

ggplot(factors, aes(x = hour,)) +
  geom_line(aes(y = factor)) +
  scale_color_manual(values = cbPalette) +
  theme_light()