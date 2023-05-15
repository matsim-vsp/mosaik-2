library(modelr)
library(tidyverse)

# read palm dataprint("exposure data")
a <- read_csv("C:/Users/janek/Documents/work/palm/berlin_with_geometry_attributes/palm-output/photoshade_6km10m_lod2.day2-link-exposure-contributions.csv")

# read matsim smoothing data
print("read activity times")
b <- read_csv("C:/Users/janek/Documents/work/palm/berlin_with_geometry_attributes/output/berlin-with-geometry-attributes.activity_times_per_link.csv")

joined <- a %>%
  inner_join(b, by = join_by("id", "time"), suffix = c("-exp", "-act-times")) %>%
  mutate(exposure = NO2 + PM10)

print(joined, n = 20)

plot <- ggplot(data = joined, mapping = aes(x = exposure, y = value)) +
  geom_point(shape = ".") +
  #ylim(0, 5) +
  # xlim(0, 2e05) +
  facet_wrap(vars(time))
ggsave(plot, filename = "exposure-fit.png")